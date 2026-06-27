package com.app.url_shortener.iam.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

import com.app.url_shortener.iam.application.command.LoginCommand;
import com.app.url_shortener.iam.application.port.output.AccessTokenIssuerPort;
import com.app.url_shortener.iam.application.port.output.AuthenticateCredentialsPort;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.RefreshTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.application.port.output.model.IssuedAccessToken;
import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.iam.application.usecase.impl.LoginUseCaseImpl;
import com.app.url_shortener.iam.domain.exception.auth.InvalidCredentialsException;
import com.app.url_shortener.iam.domain.model.RefreshToken;
import com.app.url_shortener.shared.ratelimit.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Login")
class LoginUseCaseTest {

  @Mock private AccessTokenIssuerPort accessTokenIssuerPort;

  @Mock private AuthenticateCredentialsPort authenticateCredentialsPort;

  @Mock private SecureTokenGeneratorPort secureTokenGeneratorPort;

  @Mock private RefreshTokenRepositoryPort refreshTokenRepositoryPort;

  @Mock private CheckAuthRateLimitPort checkAuthRateLimitPort;

  @Captor private ArgumentCaptor<RefreshToken> refreshTokenCaptor;

  @InjectMocks private LoginUseCaseImpl loginUseCase;

  @Nested
  @DisplayName("Execução do Login")
  class ExecuteTests {

    @Test
    @DisplayName("Deve autenticar credenciais, emitir tokens e salvar refresh token com sucesso")
    void shouldAuthenticateCredentialsIssueTokensAndSaveRefreshTokenSuccessfully() {
      // 1. Arrange
      var command = new LoginCommand(" USER@EMAIL.COM ", "secure-password", "203.0.113.10");
      var authenticatedUser = authenticatedUser();
      var rawRefreshToken = "raw-refresh-token";
      var refreshTokenHash = "hashed-refresh-token";
      var issuedAccessToken = new IssuedAccessToken("jwt-access-token", 3600L);
      var expiresInSeconds = 3_600L;
      var normalizedEmail = "user@email.com";
      var clientIp = "203.0.113.10";

      given(authenticateCredentialsPort.authenticate(normalizedEmail, command.password()))
          .willReturn(authenticatedUser);
      given(accessTokenIssuerPort.issue(authenticatedUser)).willReturn(issuedAccessToken);
      given(secureTokenGeneratorPort.generateRandomToken()).willReturn(rawRefreshToken);
      given(secureTokenGeneratorPort.hashToken(rawRefreshToken)).willReturn(refreshTokenHash);

      // 2. Act
      var result = loginUseCase.execute(command);

      // 3. Assert
      assertAll(
          () -> assertThat(result.refreshToken()).isEqualTo(rawRefreshToken),
          () -> assertThat(result.accessToken()).isEqualTo(issuedAccessToken.value()),
          () -> assertThat(result.tokenType()).isEqualTo("Bearer"),
          () -> assertThat(result.expiresInSeconds()).isEqualTo(expiresInSeconds),
          () -> assertThat(result.user()).isEqualTo(authenticatedUser));

      verify(refreshTokenRepositoryPort).save(refreshTokenCaptor.capture());
      var savedRefreshToken = refreshTokenCaptor.getValue();

      assertAll(
          () -> assertThat(savedRefreshToken.getId()).isNotNull(),
          () -> assertThat(savedRefreshToken.getUserId()).isEqualTo(authenticatedUser.id()),
          () -> assertThat(savedRefreshToken.getTokenHash()).isEqualTo(refreshTokenHash),
          () -> assertThat(savedRefreshToken.isRevoked()).isFalse(),
          () -> assertThat(savedRefreshToken.getCreatedAt()).isNotNull(),
          () ->
              assertThat(savedRefreshToken.getExpiresAt())
                  .isAfter(savedRefreshToken.getCreatedAt()));

      InOrder inOrder = inOrder(checkAuthRateLimitPort, authenticateCredentialsPort);
      inOrder.verify(checkAuthRateLimitPort).checkLogin(clientIp, normalizedEmail);
      inOrder.verify(authenticateCredentialsPort).authenticate(normalizedEmail, command.password());

      verify(accessTokenIssuerPort).issue(authenticatedUser);
      verify(secureTokenGeneratorPort).generateRandomToken();
      verify(secureTokenGeneratorPort).hashToken(rawRefreshToken);
      verifyNoMoreInteractions(
          checkAuthRateLimitPort,
          accessTokenIssuerPort,
          authenticateCredentialsPort,
          secureTokenGeneratorPort,
          refreshTokenRepositoryPort);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar login quando o email for nulo, vazio ou em branco")
    void shouldRejectLoginWhenEmailIsNullEmptyOrBlank(String email) {
      // 1. Arrange
      var command = new LoginCommand(email, "secure-password", "203.0.113.10");

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> loginUseCase.execute(command));

      // 3. Assert
      throwableAssert
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessage("Credenciais inválidas.");

      verifyNoInteractions(
          checkAuthRateLimitPort,
          accessTokenIssuerPort,
          authenticateCredentialsPort,
          secureTokenGeneratorPort,
          refreshTokenRepositoryPort);
    }

    @Test
    @DisplayName("Deve propagar exceção quando as credenciais forem inválidas")
    void shouldPropagateExceptionWhenCredentialsAreInvalid() {
      // 1. Arrange
      var command = new LoginCommand("user@email.com", "wrong-password", "203.0.113.10");
      var exception = new InvalidCredentialsException();

      given(authenticateCredentialsPort.authenticate(command.email(), command.password()))
          .willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> loginUseCase.execute(command));

      // 3. Assert
      throwableAssert
          .isInstanceOf(InvalidCredentialsException.class)
          .hasMessage("Credenciais inválidas.");

      verify(checkAuthRateLimitPort).checkLogin(command.clientIp(), command.email());
      verify(authenticateCredentialsPort).authenticate(command.email(), command.password());
      verifyNoInteractions(
          accessTokenIssuerPort, secureTokenGeneratorPort, refreshTokenRepositoryPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, authenticateCredentialsPort);
    }

    @Test
    @DisplayName("Deve propagar rate limit e não autenticar credenciais quando login for negado")
    void shouldPropagateRateLimitAndNotAuthenticateCredentialsWhenLoginIsDenied() {
      // 1. Arrange
      var command = new LoginCommand("user@email.com", "secure-password", "203.0.113.10");
      var exception = new TooManyRequestsException(Duration.ofSeconds(30));

      doThrow(exception)
          .when(checkAuthRateLimitPort)
          .checkLogin(command.clientIp(), command.email());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> loginUseCase.execute(command));

      // 3. Assert
      throwableAssert.isSameAs(exception);

      verify(checkAuthRateLimitPort).checkLogin(command.clientIp(), command.email());
      verifyNoInteractions(
          accessTokenIssuerPort,
          authenticateCredentialsPort,
          secureTokenGeneratorPort,
          refreshTokenRepositoryPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort);
    }
  }

  private AuthenticatedUserResult authenticatedUser() {
    return new AuthenticatedUserResult(
        UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac123"),
        "User Name",
        "user@email.com",
        List.of("USER"),
        List.of("url:create", "url:read"),
        "FREE");
  }
}
