package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.LoginCommand;
import com.app.url_shortener.iam.application.port.output.AccessTokenIssuerPort;
import com.app.url_shortener.iam.application.port.output.AuthenticateCredentialsPort;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.application.port.output.model.IssuedAccessToken;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.application.result.LoginResult;
import com.app.url_shortener.iam.application.usecase.LoginUseCase;
import com.app.url_shortener.iam.domain.exception.auth.InvalidCredentialsException;
import com.app.url_shortener.iam.domain.model.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginUseCaseImpl implements LoginUseCase {

  private static final String TOKEN_TYPE = "Bearer";

  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final AuthenticateCredentialsPort authenticateCredentialsPort;
  private final AccessTokenIssuerPort accessTokenIssuerPort;
  private final SecureTokenGeneratorPort secureTokenGeneratorPort;
  private final RefreshTokenRepositoryPort refreshTokenRepositoryPort;

  @Override
  @Transactional
  public LoginResult execute(LoginCommand command) {
    String email = command.email();
    if (email == null || email.isBlank()) {
      throw new InvalidCredentialsException();
    }

    checkAuthRateLimitPort.checkLogin(command.clientIp(), email);

    AuthenticatedUserResult authenticatedUser = authenticateCredentialsPort.authenticate(email, command.password());

    IssuedAccessToken issuedAccessToken = accessTokenIssuerPort.issue(authenticatedUser);

    String rawRefreshToken = secureTokenGeneratorPort.generateRandomToken();
    String refreshTokenHash = secureTokenGeneratorPort.hashToken(rawRefreshToken);
    RefreshToken refreshToken = RefreshToken.create(authenticatedUser.id(), refreshTokenHash);
    refreshTokenRepositoryPort.save(refreshToken);

    return new LoginResult(
        rawRefreshToken,
        issuedAccessToken.value(),
        TOKEN_TYPE,
        issuedAccessToken.expiresInSeconds(),
        authenticatedUser);
  }
}
