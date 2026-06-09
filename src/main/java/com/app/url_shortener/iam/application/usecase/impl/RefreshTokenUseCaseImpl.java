package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.RefreshTokenCommand;
import com.app.url_shortener.iam.application.port.output.AccessTokenIssuerPort;
import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.application.result.RefreshTokenResult;
import com.app.url_shortener.iam.application.service.CompromisedRefreshTokenRevocationService;
import com.app.url_shortener.iam.application.usecase.RefreshTokenUseCase;
import com.app.url_shortener.iam.domain.exception.auth.RefreshTokenExpiredException;
import com.app.url_shortener.iam.domain.exception.auth.TokenCompromisedException;
import com.app.url_shortener.iam.domain.exception.user.UserNotFoundException;
import com.app.url_shortener.iam.domain.model.Permission;
import com.app.url_shortener.iam.domain.model.RefreshToken;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RefreshTokenUseCaseImpl implements RefreshTokenUseCase {

  private final SecureTokenGeneratorPort secureTokenGeneratorPort;
  private final RefreshTokenRepositoryPort refreshTokenRepositoryPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final AccessTokenIssuerPort accessTokenIssuerPort;
  private final CompromisedRefreshTokenRevocationService compromisedRefreshTokenRevocationService;

  @Override
  @Transactional
  public RefreshTokenResult execute(RefreshTokenCommand command) {
    String currentRefreshTokenHash = secureTokenGeneratorPort.hashToken(command.refreshToken());
    RefreshToken currentRefreshToken = refreshTokenRepositoryPort
            .findByTokenHash(currentRefreshTokenHash)
            .orElseThrow(RefreshTokenExpiredException::new);

    Instant now = Instant.now();
    ensureRefreshTokenWasNotReused(currentRefreshToken);
    ensureRefreshTokenIsNotExpired(currentRefreshToken);

    String replacementRawRefreshToken = secureTokenGeneratorPort.generateRandomToken();
    String replacementRefreshTokenHash = secureTokenGeneratorPort.hashToken(replacementRawRefreshToken);

    RefreshToken replacementRefreshToken = RefreshToken.create(
            currentRefreshToken.getUserId(),
            replacementRefreshTokenHash
    );
    refreshTokenRepositoryPort.save(replacementRefreshToken);

    rotateCurrentRefreshTokenOrReject(
            currentRefreshTokenHash,
            currentRefreshToken,
            replacementRefreshToken.getId()
    );

    AuthenticatedUserResult authenticatedUser = loadAuthenticatedUserResult(currentRefreshToken.getUserId());
    String replacementAccessToken = accessTokenIssuerPort.issue(authenticatedUser).value();
    return new RefreshTokenResult(replacementRawRefreshToken, replacementAccessToken);
  }

  private void ensureRefreshTokenWasNotReused(RefreshToken refreshToken) {
    if (refreshToken.isRevoked()) {
      revokeAllTokensDueToCompromise(refreshToken.getUserId());
    }
  }

  private void ensureRefreshTokenIsNotExpired(RefreshToken refreshToken) {
    if (refreshToken.isExpired()) {
      throw new RefreshTokenExpiredException();
    }
  }

  private void rotateCurrentRefreshTokenOrReject(
      String currentRefreshTokenHash,
      RefreshToken currentRefreshToken,
      UUID replacementRefreshTokenId) {

    int updatedRows = refreshTokenRepositoryPort.markTokenAsRotatedIfActive(
            currentRefreshTokenHash,
            Instant.now(),
            replacementRefreshTokenId
    );
    if (updatedRows != 1) {
      revokeAllTokensDueToCompromise(currentRefreshToken.getUserId());
    }
  }

  private void revokeAllTokensDueToCompromise(UUID userId) {
    compromisedRefreshTokenRevocationService.revokeAllTokensDueToCompromise(userId);
    throw new TokenCompromisedException();
  }

  private AuthenticatedUserResult loadAuthenticatedUserResult(UUID userId) {
    UserAccount userAccount = userAccountRepositoryPort
            .findByIdWithRolesAndPermissions(userId)
            .orElseThrow(UserNotFoundException::new);

    List<String> roles = userAccount.getRoles().stream().map(Role::getName).toList();

    List<String> authorities = userAccount.getRoles().stream()
            .flatMap(role -> role.getPermissions().stream())
            .map(Permission::getName)
            .distinct()
            .toList();

    return new AuthenticatedUserResult(
        userAccount.getId(),
        userAccount.getName(),
        userAccount.getEmail(),
        roles,
        authorities,
        userAccount.getPlan().name());
  }
}
