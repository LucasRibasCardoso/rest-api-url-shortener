package com.app.url_shortener.iam.application.usecase;

import com.app.url_shortener.iam.application.command.VerifyEmailCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenPort;
import com.app.url_shortener.iam.application.port.output.RoleRepositoryPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.usecase.impl.VerifyEmailUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.auth.InvalidOrExpiredEmailVerificationCodeException;
import com.app.url_shortener.iam.domain.exception.user.UserNotFoundException;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Verificação de E-mail")
class VerifyEmailUseCaseTest {

  private static final String SUCCESS_MESSAGE =
          "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";

  @Mock
  private RoleRepositoryPort roleRepositoryPort;

  @Mock
  private UserAccountRepositoryPort userAccountRepositoryPort;

  @Mock
  private EmailVerificationTokenPort emailVerificationTokenPort;

  @Mock
  private CheckAuthRateLimitPort checkAuthRateLimitPort;

  @Captor
  private ArgumentCaptor<UserAccount> userAccountCaptor;

  @InjectMocks
  private VerifyEmailUseCaseImpl verifyEmailUseCase;

  @Nested
  @DisplayName("Execução da verificação de e-mail")
  class ExecuteTests {

    @Test
    @DisplayName("Deve consumir token, ativar usuário, atribuir role padrão e salvar conta")
    void shouldConsumeTokenActivateUserAssignDefaultRoleAndSaveAccount() {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var command = new VerifyEmailCommand(" USER@EMAIL.COM ", code);
      var token = validToken("user@email.com", code);
      var pendingUser = pendingUser();
      var defaultRole = Role.create("ROLE_USER", Set.of());

      given(emailVerificationTokenPort.consumeByEmailAndCode("user@email.com", code)).willReturn(Optional.of(token));
      given(userAccountRepositoryPort.findByEmailWithRoles("user@email.com")).willReturn(Optional.of(pendingUser));
      given(roleRepositoryPort.findDefaultRole()).willReturn(defaultRole);

      // 2. Act
      var result = verifyEmailUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(SUCCESS_MESSAGE);

      verify(userAccountRepositoryPort).save(userAccountCaptor.capture());
      var savedUser = userAccountCaptor.getValue();

      assertAll(
              () -> assertThat(savedUser.getId()).isEqualTo(pendingUser.getId()),
              () -> assertThat(savedUser.isEmailVerified()).isTrue(),
              () -> assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE),
              () -> assertThat(savedUser.getRoles()).containsExactly(defaultRole)
      );

      verify(userAccountRepositoryPort).findByEmailWithRoles("user@email.com");
      verify(roleRepositoryPort).findDefaultRole();

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail("user@email.com");
      inOrder.verify(emailVerificationTokenPort).consumeByEmailAndCode("user@email.com", code);

      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort, userAccountRepositoryPort, roleRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o token de verificação não existir")
    void shouldThrowExceptionWhenVerificationTokenDoesNotExist() {
      // 1. Arrange
      var command = new VerifyEmailCommand("user@email.com", VerificationCode.of("123456"));

      given(emailVerificationTokenPort.consumeByEmailAndCode(command.email(), command.code())).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(InvalidOrExpiredEmailVerificationCodeException.class)
              .hasMessage("Código de verificação inválido ou expirado.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).consumeByEmailAndCode(command.email(), command.code());

      verifyNoInteractions(userAccountRepositoryPort, roleRepositoryPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o token de verificação estiver expirado")
    void shouldThrowExceptionWhenVerificationTokenIsExpired() {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var command = new VerifyEmailCommand("user@email.com", code);
      var expiredToken = expiredToken(command.email(), code);

      given(emailVerificationTokenPort.consumeByEmailAndCode(command.email(), code)).willReturn(Optional.of(expiredToken));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(InvalidOrExpiredEmailVerificationCodeException.class)
              .hasMessage("Código de verificação inválido ou expirado.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).consumeByEmailAndCode(command.email(), code);

      verifyNoInteractions(userAccountRepositoryPort, roleRepositoryPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort);
    }

    @Test
    @DisplayName("Deve lançar exceção genérica quando o código de verificação for inválido ou já consumido")
    void shouldThrowGenericExceptionWhenVerificationCodeIsInvalidOrAlreadyConsumed() {
      // 1. Arrange
      var command = new VerifyEmailCommand("user@email.com", VerificationCode.of("654321"));

      given(emailVerificationTokenPort.consumeByEmailAndCode(command.email(), command.code())).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(InvalidOrExpiredEmailVerificationCodeException.class)
              .hasMessage("Código de verificação inválido ou expirado.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).consumeByEmailAndCode(command.email(), command.code());

      verifyNoInteractions(userAccountRepositoryPort, roleRepositoryPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o usuário do e-mail não for encontrado")
    void shouldThrowExceptionWhenUserIsNotFound() {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var command = new VerifyEmailCommand("user@email.com", code);
      var token = validToken(command.email(), code);

      given(emailVerificationTokenPort.consumeByEmailAndCode(command.email(), code)).willReturn(Optional.of(token));
      given(userAccountRepositoryPort.findByEmailWithRoles(command.email())).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(UserNotFoundException.class)
              .hasMessage("Usuário não encontrado.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).consumeByEmailAndCode(command.email(), code);

      verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      verifyNoInteractions(roleRepositoryPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve propagar exceção quando a persistência da conta falhar após consumir token")
    void shouldPropagateExceptionWhenAccountPersistenceFailsAfterConsumingToken() {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var command = new VerifyEmailCommand("user@email.com", code);
      var token = validToken(command.email(), code);
      var pendingUser = pendingUser();
      var defaultRole = Role.create("ROLE_USER", Set.of());
      var exception = new IllegalStateException("Falha ao salvar usuário.");

      given(emailVerificationTokenPort.consumeByEmailAndCode(command.email(), code)).willReturn(Optional.of(token));
      given(userAccountRepositoryPort.findByEmailWithRoles(command.email())).willReturn(Optional.of(pendingUser));
      given(roleRepositoryPort.findDefaultRole()).willReturn(defaultRole);
      given(userAccountRepositoryPort.save(pendingUser)).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("Falha ao salvar usuário.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).consumeByEmailAndCode(command.email(), code);

      verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      verify(roleRepositoryPort).findDefaultRole();
      verify(userAccountRepositoryPort).save(pendingUser);
      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort, userAccountRepositoryPort, roleRepositoryPort);
    }
  }

  private EmailVerificationToken validToken(String email, VerificationCode code) {
    return EmailVerificationToken.create(
            UUID.fromString("019a19f7-9705-7954-a0df-b93678630001"),
            email,
            code,
            Instant.now().plus(10, ChronoUnit.MINUTES)
    );
  }

  private EmailVerificationToken expiredToken(String email, VerificationCode code) {
    return EmailVerificationToken.create(
            UUID.fromString("019a19f7-9705-7954-a0df-b93678630002"),
            email,
            code,
            Instant.now().minus(1, ChronoUnit.MINUTES)
    );
  }

  private UserAccount pendingUser() {
    return UserAccount.restore(
            UUID.fromString("019a19f7-9705-7954-a0df-b93678630003"),
            "User Name",
            "user@email.com",
            "encoded-password",
            UserStatus.PENDING_EMAIL_VERIFICATION,
            PlanType.FREE,
            false,
            Set.of()
    );
  }
}
