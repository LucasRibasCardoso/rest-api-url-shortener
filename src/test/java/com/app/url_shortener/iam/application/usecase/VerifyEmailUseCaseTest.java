package com.app.url_shortener.iam.application.usecase;

import com.app.url_shortener.iam.application.command.VerifyEmailCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenPort;
import com.app.url_shortener.iam.application.port.output.RoleRepositoryPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.usecase.impl.VerifyEmailUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationTokenExpiredException;
import com.app.url_shortener.iam.domain.exception.user.InvalidVerificationCodeException;
import com.app.url_shortener.iam.domain.exception.user.UserNotFoundException;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import org.junit.jupiter.api.AfterEach;
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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

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

  @AfterEach
  void tearDownTransactionSynchronization() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Nested
  @DisplayName("Execução da verificação de e-mail")
  class ExecuteTests {

    @Test
    @DisplayName("Deve ativar usuário, atribuir role padrão, salvar conta e excluir token após commit")
    void shouldActivateUserAssignDefaultRoleSaveAccountAndDeleteTokenAfterCommit() {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var command = new VerifyEmailCommand(" USER@EMAIL.COM ", code);
      var token = validToken("user@email.com", code);
      var pendingUser = pendingUser();
      var defaultRole = Role.create("ROLE_USER", Set.of());

      TransactionSynchronizationManager.initSynchronization();
      given(emailVerificationTokenPort.findByEmail("user@email.com")).willReturn(Optional.of(token));
      given(userAccountRepositoryPort.findByEmailWithRoles("user@email.com")).willReturn(Optional.of(pendingUser));
      given(roleRepositoryPort.findDefaultRole()).willReturn(defaultRole);

      // 2. Act
      var result = verifyEmailUseCase.execute(command);

      // 3. Assert
      verify(emailVerificationTokenPort, never()).deleteByEmail(anyString());

      TransactionSynchronizationManager.getSynchronizations()
              .forEach(TransactionSynchronization::afterCommit);

      assertThat(result.message()).isEqualTo(SUCCESS_MESSAGE);

      verify(userAccountRepositoryPort).save(userAccountCaptor.capture());
      var savedUser = userAccountCaptor.getValue();

      assertAll(
              () -> assertThat(savedUser.getId()).isEqualTo(pendingUser.getId()),
              () -> assertThat(savedUser.isEmailVerified()).isTrue(),
              () -> assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE),
              () -> assertThat(savedUser.getRoles()).containsExactly(defaultRole)
      );

      verify(emailVerificationTokenPort).findByEmail("user@email.com");
      verify(userAccountRepositoryPort).findByEmailWithRoles("user@email.com");
      verify(roleRepositoryPort).findDefaultRole();
      verify(emailVerificationTokenPort).deleteByEmail("user@email.com");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail("user@email.com");
      inOrder.verify(emailVerificationTokenPort).findByEmail("user@email.com");

      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort, userAccountRepositoryPort, roleRepositoryPort);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o token de verificação não existir")
    void shouldThrowExceptionWhenVerificationTokenDoesNotExist() {
      // 1. Arrange
      var command = new VerifyEmailCommand("user@email.com", VerificationCode.of("123456"));

      given(emailVerificationTokenPort.findByEmail(command.email())).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(EmailVerificationTokenExpiredException.class)
              .hasMessage("Token de verificação de email expirado.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).findByEmail(command.email());

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

      given(emailVerificationTokenPort.findByEmail(command.email())).willReturn(Optional.of(expiredToken));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(EmailVerificationTokenExpiredException.class)
              .hasMessage("Token de verificação de email expirado.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).findByEmail(command.email());

      verifyNoInteractions(userAccountRepositoryPort, roleRepositoryPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o código de verificação não corresponder ao token")
    void shouldThrowExceptionWhenVerificationCodeDoesNotMatchToken() {
      // 1. Arrange
      var storedCode = VerificationCode.of("123456");
      var command = new VerifyEmailCommand("user@email.com", VerificationCode.of("654321"));
      var token = validToken(command.email(), storedCode);

      given(emailVerificationTokenPort.findByEmail(command.email())).willReturn(Optional.of(token));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(InvalidVerificationCodeException.class)
              .hasMessage("Código de verificação inválido.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).findByEmail(command.email());

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

      given(emailVerificationTokenPort.findByEmail(command.email())).willReturn(Optional.of(token));
      given(userAccountRepositoryPort.findByEmailWithRoles(command.email())).willReturn(Optional.empty());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> verifyEmailUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(UserNotFoundException.class)
              .hasMessage("Usuário não encontrado.");

      InOrder inOrder = inOrder(checkAuthRateLimitPort, emailVerificationTokenPort);
      inOrder.verify(checkAuthRateLimitPort).checkVerifyEmail(command.email());
      inOrder.verify(emailVerificationTokenPort).findByEmail(command.email());

      verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      verifyNoInteractions(roleRepositoryPort);
      verify(emailVerificationTokenPort, never()).deleteByEmail(anyString());
      verifyNoMoreInteractions(checkAuthRateLimitPort, emailVerificationTokenPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve propagar exceção e não excluir token quando a persistência da conta falhar")
    void shouldPropagateExceptionAndNotDeleteTokenWhenAccountPersistenceFails() {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var command = new VerifyEmailCommand("user@email.com", code);
      var token = validToken(command.email(), code);
      var pendingUser = pendingUser();
      var defaultRole = Role.create("ROLE_USER", Set.of());
      var exception = new IllegalStateException("Falha ao salvar usuário.");

      given(emailVerificationTokenPort.findByEmail(command.email())).willReturn(Optional.of(token));
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
      inOrder.verify(emailVerificationTokenPort).findByEmail(command.email());

      verify(userAccountRepositoryPort).findByEmailWithRoles(command.email());
      verify(roleRepositoryPort).findDefaultRole();
      verify(userAccountRepositoryPort).save(pendingUser);
      verify(emailVerificationTokenPort, never()).deleteByEmail(anyString());
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
