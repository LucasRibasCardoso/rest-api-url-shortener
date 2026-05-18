package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.RefreshTokenCommand;
import com.app.url_shortener.iam.application.port.output.IssueAccessTokenPort;
import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.application.result.RefreshTokenResult;
import com.app.url_shortener.iam.application.service.RefreshTokenSecurityService;
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

  private final IssueAccessTokenPort accessTokenPort;
  private final SecureTokenGeneratorPort tokenGenerator;
  private final UserAccountRepositoryPort userRepository;
  private final RefreshTokenRepositoryPort tokenRepository;
  private final RefreshTokenSecurityService refreshTokenSecurityService;

  @Override
  @Transactional
  public RefreshTokenResult execute(RefreshTokenCommand command) {
    String oldTokenHash = tokenGenerator.hashToken(command.refreshToken());
    RefreshToken oldToken = tokenRepository.findByTokenHash(oldTokenHash)
            .orElseThrow(RefreshTokenExpiredException::new);

    Instant now = Instant.now();
    ensureTokenWasNotReused(oldToken);
    ensureTokenIsNotExpired(oldToken, now);

    String newRawRefreshToken = tokenGenerator.generateRandomToken();
    String newRefreshTokenHash = tokenGenerator.hashToken(newRawRefreshToken);

    RefreshToken newRefreshToken = RefreshToken.create(oldToken.getUserId(), newRefreshTokenHash);
    tokenRepository.save(newRefreshToken);

    rotateOldTokenOrReject(oldTokenHash, oldToken, now, newRefreshToken.getId());

    AuthenticatedUserResult authenticatedUser = buildAuthenticatedUser(oldToken.getUserId());
    String newAccessToken = accessTokenPort.getToken(authenticatedUser);
    return new RefreshTokenResult(newRawRefreshToken, newAccessToken);
  }

  private void ensureTokenWasNotReused(RefreshToken token) {
    if (token.isRevoked()) {
      revokeAllTokensDueToCompromise(token.getUserId());
    }
  }

  private void ensureTokenIsNotExpired(RefreshToken token, Instant now) {
    if (token.isExpired(now)) {
      throw new RefreshTokenExpiredException();
    }
  }

  private void rotateOldTokenOrReject(
          String oldTokenHash,
          RefreshToken oldToken,
          Instant now,
          UUID newRefreshTokenId) {
    int updatedRows = tokenRepository.markTokenAsRotatedIfActive(oldTokenHash, now, newRefreshTokenId);
    if (updatedRows != 1) {
      revokeAllTokensDueToCompromise(oldToken.getUserId());
    }
  }

  private void revokeAllTokensDueToCompromise(UUID userId) {
    refreshTokenSecurityService.revokeAllTokensDueToCompromise(userId);
    throw new TokenCompromisedException();
  }

  private AuthenticatedUserResult buildAuthenticatedUser(UUID userId) {
    UserAccount user = userRepository.findByIdWithRolesAndPermissions(userId).orElseThrow(UserNotFoundException::new);

    List<String> roles = user.getRoles().stream().map(Role::getName).toList();

    List<String> authorities =
            user.getRoles().stream()
                    .flatMap(role -> role.getPermissions().stream())
                    .map(Permission::getName)
                    .distinct()
                    .toList();

    return new AuthenticatedUserResult(
            user.getId(),
            user.getName(),
            user.getEmail(),
            roles,
            authorities,
            user.getPlan().name()
    );
  }
}
