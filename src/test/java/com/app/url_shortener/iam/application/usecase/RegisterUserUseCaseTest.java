package com.app.url_shortener.iam.application.usecase;

import com.app.url_shortener.iam.application.command.RegisterUserCommand;
import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventPublisherPort;
import com.app.url_shortener.iam.application.port.output.PasswordEncoderPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.usecase.impl.RegisterUserUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.domain.model.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

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
  private EmailVerificationEventPublisherPort emailVerificationEventPublisherPort;

  @Mock
  private UserAccountRepositoryPort userAccountRepositoryPort;

  @Captor
  private ArgumentCaptor<UserAccount> userAccountCaptor;

  @Captor
  private ArgumentCaptor<EmailVerificationRequestedEvent> emailVerificationEventCaptor;

  @InjectMocks
  private RegisterUserUseCaseImpl registerUserUseCase;

  @Nested
  @DisplayName("Execução do registro de usuário")
  class ExecuteTests {

    @Test
    @DisplayName("Deve criar usuário pendente e publicar evento de registro com sucesso")
    void shouldCreatePendingUserAndPublishRegisterEventSuccessfully() {
      // 1. Arrange
      var command = new RegisterUserCommand("  User   Name  ", " USER@EMAIL.COM ", "raw-password");
      var passwordHash = "encoded-password";
      var savedUser = savedPendingUser();
      var beforeExecution = Instant.now();

      given(passwordEncoderPort.encode(command.password())).willReturn(passwordHash);
      given(userAccountRepositoryPort.create(any(UserAccount.class))).willReturn(savedUser);

      // 2. Act
      var result = registerUserUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(SUCCESS_MESSAGE);

      verify(userAccountRepositoryPort).create(userAccountCaptor.capture());
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

      verify(emailVerificationEventPublisherPort).publish(emailVerificationEventCaptor.capture());
      var publishedEvent = emailVerificationEventCaptor.getValue();

      assertAll(
              () -> assertThat(publishedEvent.userId()).isEqualTo(savedUser.getId()),
              () -> assertThat(publishedEvent.email()).isEqualTo(savedUser.getEmail()),
              () -> assertThat(publishedEvent.reason()).isEqualTo(EmailVerificationReason.REGISTER),
              () -> assertThat(publishedEvent.eventId()).isNotNull(),
              () -> assertThat(publishedEvent.occurredAt()).isAfterOrEqualTo(beforeExecution)
      );

      verify(passwordEncoderPort).encode(command.password());
      verifyNoMoreInteractions(
              passwordEncoderPort,
              emailVerificationEventPublisherPort,
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
              emailVerificationEventPublisherPort);
      verifyNoMoreInteractions(passwordEncoderPort);
    }

    @Test
    @DisplayName("Deve propagar exceção e não armazenar token quando a persistência do usuário falhar")
    void shouldPropagateExceptionAndNotStoreTokenWhenUserPersistenceFails() {
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
      verifyNoInteractions(emailVerificationEventPublisherPort);
      verifyNoMoreInteractions(passwordEncoderPort, userAccountRepositoryPort);
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
