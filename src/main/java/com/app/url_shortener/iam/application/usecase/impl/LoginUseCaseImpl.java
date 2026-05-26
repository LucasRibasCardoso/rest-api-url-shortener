package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.LoginCommand;
import com.app.url_shortener.iam.application.port.output.*;
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

  private final SecureTokenGeneratorPort tokenGenerator;
  private final IssueAccessTokenPort issueAccessTokenPort;
  private final RefreshTokenRepositoryPort tokenRepository;
  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final AuthenticateCredentialsPort authenticateCredentialsPort;

  @Override
  @Transactional
  public LoginResult execute(LoginCommand command) {
    String email = command.email();
    if (email == null || email.isBlank()) {
      throw new InvalidCredentialsException();
    }

    checkAuthRateLimitPort.checkLogin(command.clientIp(), command.email());

    AuthenticatedUserResult authenticatedUser = authenticateCredentialsPort.authenticate(email, command.password());

    String accessToken = issueAccessTokenPort.getToken(authenticatedUser);

    String rawRefreshToken = tokenGenerator.generateRandomToken();
    String tokenHash = tokenGenerator.hashToken(rawRefreshToken);
    RefreshToken refreshToken = RefreshToken.create(authenticatedUser.id(), tokenHash);
    tokenRepository.save(refreshToken);

    long expiresAt = issueAccessTokenPort.getExpiresInSeconds();

    return new LoginResult(rawRefreshToken, accessToken, TOKEN_TYPE, expiresAt, authenticatedUser);
  }
}
