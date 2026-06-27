package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.LogoutCommand;
import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.application.usecase.LogoutUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LogoutUseCaseImpl implements LogoutUseCase {

  private final SecureTokenGeneratorPort secureTokenGeneratorPort;
  private final RefreshTokenRepositoryPort refreshTokenRepositoryPort;

  @Override
  @Transactional
  public void execute(LogoutCommand command) {
    String rawRefreshToken = command.refreshToken();
    if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
      return;
    }

    String refreshTokenHash = secureTokenGeneratorPort.hashToken(rawRefreshToken);
    refreshTokenRepositoryPort.revokeActiveTokenByHash(refreshTokenHash);
  }
}
