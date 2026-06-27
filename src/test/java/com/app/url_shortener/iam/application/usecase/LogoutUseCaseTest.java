package com.app.url_shortener.iam.application.usecase;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.app.url_shortener.iam.application.command.LogoutCommand;
import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.application.usecase.impl.LogoutUseCaseImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Logout")
class LogoutUseCaseTest {

  @Mock private SecureTokenGeneratorPort secureTokenGeneratorPort;

  @Mock private RefreshTokenRepositoryPort refreshTokenRepositoryPort;

  @InjectMocks private LogoutUseCaseImpl logoutUseCase;

  @Nested
  @DisplayName("Execução do Logout")
  class ExecuteTests {

    @Test
    @DisplayName("Deve gerar hash do refresh token e revogar o token ativo com sucesso")
    void shouldHashRefreshTokenAndRevokeActiveTokenSuccessfully() {
      // 1. Arrange
      var rawRefreshToken = "raw-refresh-token";
      var tokenHash = "hashed-refresh-token";
      var command = new LogoutCommand(rawRefreshToken);

      given(secureTokenGeneratorPort.hashToken(rawRefreshToken)).willReturn(tokenHash);

      // 2. Act
      logoutUseCase.execute(command);

      // 3. Assert
      verify(secureTokenGeneratorPort).hashToken(rawRefreshToken);
      verify(refreshTokenRepositoryPort).revokeActiveTokenByHash(tokenHash);
      verifyNoMoreInteractions(secureTokenGeneratorPort, refreshTokenRepositoryPort);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Deve ignorar logout quando o refresh token for nulo, vazio ou em branco")
    void shouldIgnoreLogoutWhenRefreshTokenIsNullEmptyOrBlank(String refreshToken) {
      // 1. Arrange
      var command = new LogoutCommand(refreshToken);

      // 2. Act
      logoutUseCase.execute(command);

      // 3. Assert
      verifyNoInteractions(secureTokenGeneratorPort, refreshTokenRepositoryPort);
    }

    @Test
    @DisplayName("Deve propagar exceção quando a geração do hash do refresh token falhar")
    void shouldPropagateExceptionWhenRefreshTokenHashingFails() {
      // 1. Arrange
      var rawRefreshToken = "raw-refresh-token";
      var command = new LogoutCommand(rawRefreshToken);
      var exception = new IllegalArgumentException("Refresh token inválido.");

      given(secureTokenGeneratorPort.hashToken(rawRefreshToken)).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> logoutUseCase.execute(command));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Refresh token inválido.");

      verify(secureTokenGeneratorPort).hashToken(rawRefreshToken);
      verifyNoInteractions(refreshTokenRepositoryPort);
      verifyNoMoreInteractions(secureTokenGeneratorPort);
    }
  }
}
