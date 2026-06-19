package com.app.url_shortener.iam.application.usecase;

import com.app.url_shortener.iam.application.command.RegisterUserCommand;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.application.port.output.EmailVerificationOutboxPort;
import com.app.url_shortener.iam.application.port.output.PasswordEncoderPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.usecase.impl.RegisterUserUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.user.EmailAlreadyRegisteredException;
import com.app.url_shortener.iam.domain.model.UserAccount;
import java.util.Set;
import java.util.UUID;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Registro de Usuário")
class RegisterUserUseCaseTest {

  private static final String SUCCESS_MESSAGE = "Enviamos um código de verificação para o seu e-mail.";

  @Mock
  private PasswordEncoderPort passwordEncoderPort;

  @Mock
  private EmailVerificationOutboxPort emailVerificationEventPort;

  @Mock
  private UserAccountRepositoryPort userAccountRepositoryPort;

  @Captor
  private ArgumentCaptor<UserAccount> userAccountCaptor;

  @InjectMocks
  private RegisterUserUseCaseImpl registerUserUseCase;

  @Nested
  @DisplayName("Execução do registro de usuário")
  class ExecuteTests {

    @Test
    @DisplayName("Deve salvar usuário pendente e solicitar evento de verificação com sucesso")
    void shouldCreatePendingUserAndPublishRegisterEventSuccessfully() {
      // 1. Arrange
      var command = new RegisterUserCommand("  User   Name  ", " USER@EMAIL.COM ", "raw-password");
      var passwordHash = "encoded-password";
      var savedUser = savedPendingUser();

      given(passwordEncoderPort.encode(command.password())).willReturn(passwordHash);
      given(userAccountRepositoryPort.create(any(UserAccount.class))).willReturn(savedUser);

      // 2. Act
      var result = registerUserUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(SUCCESS_MESSAGE);

      InOrder inOrder =
          inOrder(passwordEncoderPort, userAccountRepositoryPort, emailVerificationEventPort);
      inOrder.verify(passwordEncoderPort).encode(command.password());
      inOrder.verify(userAccountRepositoryPort).create(userAccountCaptor.capture());
      var userToPersist = userAccountCaptor.getValue();

      assertAll(
              () -> assertThat(userToPersist.getId()).isNotNull(),
              () -> assertThat(userToPersist.getName()).isEqualTo("User Name"),
              () -> assertThat(userToPersist.getEmail()).isEqualTo("user@email.com"),
              () -> assertThat(userToPersist.getPasswordHash()).isEqualTo(passwordHash),
              () -> assertThat(userToPersist.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION),
              () -> assertThat(userToPersist.getPlan()).isEqualTo(PlanType.FREE),
              () -> assertThat(userToPersist.isEmailVerified()).isFalse(),
              () -> assertThat(userToPersist.getRoles()).isEmpty()
      );

      inOrder.verify(emailVerificationEventPort).publishEmailVerificationRequestedEvent(
              savedUser.getId(),
              savedUser.getEmail(),
              EmailDispatchReason.REGISTER
      );

      verifyNoMoreInteractions(
              passwordEncoderPort,
              emailVerificationEventPort,
              userAccountRepositoryPort
      );
    }

    @Test
    @DisplayName("Deve propagar exceção e não persistir usuário quando a criptografia da senha falhar")
    void shouldPropagateExceptionAndNotPersistUserWhenPasswordEncodingFails() {
      // 1. Arrange
      var command = new RegisterUserCommand("User Name", "user@email.com", "raw-password");
      var exception = new IllegalStateException("Falha ao criptografar senha.");

      given(passwordEncoderPort.encode(command.password())).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> registerUserUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("Falha ao criptografar senha.");

      verify(passwordEncoderPort).encode(command.password());
      verifyNoInteractions(
              userAccountRepositoryPort,
              emailVerificationEventPort);
      verifyNoMoreInteractions(passwordEncoderPort);
    }

    @Test
    @DisplayName("Deve propagar exceção e não solicitar evento quando a persistência do usuário falhar")
    void shouldPropagateExceptionAndNotPublishEventWhenUserPersistenceFails() {
      // 1. Arrange
      var command = new RegisterUserCommand("User Name", "user@email.com", "raw-password");
      var passwordHash = "encoded-password";
      var exception = new IllegalStateException("Falha ao salvar usuário.");

      given(passwordEncoderPort.encode(command.password())).willReturn(passwordHash);
      given(userAccountRepositoryPort.create(any(UserAccount.class))).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> registerUserUseCase.execute(command));

      // 3. Assert
      throwableAssert
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("Falha ao salvar usuário.");

      verify(passwordEncoderPort).encode(command.password());
      verify(userAccountRepositoryPort).create(any(UserAccount.class));
      verifyNoInteractions(emailVerificationEventPort);
      verifyNoMoreInteractions(passwordEncoderPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Não deve solicitar evento quando o e-mail já estiver registrado")
    void shouldNotPublishEventWhenEmailIsAlreadyRegistered() {
      // 1. Arrange
      var command = new RegisterUserCommand("User Name", "user@email.com", "raw-password");
      var passwordHash = "encoded-password";
      var exception = new EmailAlreadyRegisteredException();

      given(passwordEncoderPort.encode(command.password())).willReturn(passwordHash);
      given(userAccountRepositoryPort.create(any(UserAccount.class))).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> registerUserUseCase.execute(command));

      // 3. Assert
      throwableAssert.isSameAs(exception);

      verify(passwordEncoderPort).encode(command.password());
      verify(userAccountRepositoryPort).create(any(UserAccount.class));
      verifyNoInteractions(emailVerificationEventPort);
      verifyNoMoreInteractions(passwordEncoderPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve propagar exceção quando a solicitação do evento de verificação falhar")
    void shouldPropagateExceptionWhenEmailVerificationEventRequestFails() {
      // 1. Arrange
      var command = new RegisterUserCommand("User Name", "user@email.com", "raw-password");
      var passwordHash = "encoded-password";
      var savedUser = savedPendingUser();
      var exception = new IllegalStateException("Falha ao salvar evento no Outbox.");

      given(passwordEncoderPort.encode(command.password())).willReturn(passwordHash);
      given(userAccountRepositoryPort.create(any(UserAccount.class))).willReturn(savedUser);
      doThrow(exception)
          .when(emailVerificationEventPort)
          .publishEmailVerificationRequestedEvent(
                  savedUser.getId(),
                  savedUser.getEmail(),
                  EmailDispatchReason.REGISTER
          );

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> registerUserUseCase.execute(command));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Falha ao salvar evento no Outbox.");

      InOrder inOrder =
          inOrder(passwordEncoderPort, userAccountRepositoryPort, emailVerificationEventPort);
      inOrder.verify(passwordEncoderPort).encode(command.password());
      inOrder.verify(userAccountRepositoryPort).create(any(UserAccount.class));
      inOrder
          .verify(emailVerificationEventPort)
          .publishEmailVerificationRequestedEvent(
              savedUser.getId(),
                  savedUser.getEmail(),
                  EmailDispatchReason.REGISTER
          );
      verifyNoMoreInteractions(
          passwordEncoderPort, userAccountRepositoryPort, emailVerificationEventPort);
    }
  }

  private UserAccount savedPendingUser() {
    return UserAccount.restore(
            UUID.fromString("019a178e-4062-7e7d-8589-954203d08001"),
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
