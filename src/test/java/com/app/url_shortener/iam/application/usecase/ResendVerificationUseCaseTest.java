package com.app.url_shortener.iam.application.usecase;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationOutboxPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.usecase.impl.ResendVerificationUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.model.UserAccount;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Reenvio de Verificação")
class ResendVerificationUseCaseTest {

  private static final String RESPONSE_MESSAGE = "Enviamos um novo código de verificação para o seu e-mail.";

  @Mock
  private EmailVerificationOutboxPort emailVerificationEventPort;

  @Mock
  private UserAccountRepositoryPort userAccountRepositoryPort;

  @Mock
  private CheckAuthRateLimitPort checkAuthRateLimitPort;

  @InjectMocks
  private ResendVerificationUseCaseImpl resendVerificationUseCase;

  @Nested
  @DisplayName("Execução do reenvio de verificação")
  class ExecuteTests {

    @Test
    @DisplayName("Deve publicar evento de reenvio quando o usuário estiver pendente")
    void shouldPublishResendEventWhenUserIsPending() {
      // 1. Arrange
      var command = new ResendVerificationCommand(" USER@EMAIL.COM ");
      var pendingUser = pendingUser();

      given(userAccountRepositoryPort.findByEmail("user@email.com")).willReturn(Optional.of(pendingUser));

      // 2. Act
      var result = resendVerificationUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(RESPONSE_MESSAGE);

      verify(emailVerificationEventPort).publishEmailVerificationRequestedEvent(
              pendingUser.getId(),
              pendingUser.getEmail(),
              EmailVerificationReason.RESEND);

      InOrder inOrder = inOrder(checkAuthRateLimitPort, userAccountRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification("user@email.com");
      inOrder.verify(userAccountRepositoryPort).findByEmail("user@email.com");

      verifyNoMoreInteractions(
              checkAuthRateLimitPort,
              userAccountRepositoryPort,
              emailVerificationEventPort);
    }

    @Test
    @DisplayName("Deve retornar mensagem padrão sem publicar evento quando o usuário não existir")
    void shouldReturnDefaultMessageWithoutPublishingEventWhenUserDoesNotExist() {
      // 1. Arrange
      var command = new ResendVerificationCommand("unknown@email.com");

      given(userAccountRepositoryPort.findByEmail(command.email())).willReturn(Optional.empty());

      // 2. Act
      var result = resendVerificationUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(RESPONSE_MESSAGE);

      InOrder inOrder = inOrder(checkAuthRateLimitPort, userAccountRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification(command.email());
      inOrder.verify(userAccountRepositoryPort).findByEmail(command.email());

      verifyNoInteractions(emailVerificationEventPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve retornar mensagem padrão sem publicar evento quando o usuário não estiver pendente")
    void shouldReturnDefaultMessageWithoutPublishingEventWhenUserIsNotPending() {
      // 1. Arrange
      var command = new ResendVerificationCommand("user@email.com");
      var activeUser = activeUser();

      given(userAccountRepositoryPort.findByEmail(command.email())).willReturn(Optional.of(activeUser));

      // 2. Act
      var result = resendVerificationUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(RESPONSE_MESSAGE);

      InOrder inOrder = inOrder(checkAuthRateLimitPort, userAccountRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification(command.email());
      inOrder.verify(userAccountRepositoryPort).findByEmail(command.email());

      verifyNoInteractions(emailVerificationEventPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, userAccountRepositoryPort);
    }

  }

  private UserAccount pendingUser() {
    return UserAccount.restore(
            UUID.fromString("019a19e6-fc96-7e7c-996e-86d7c3470001"),
            "User Name",
            "user@email.com",
            "encoded-password",
            UserStatus.PENDING_EMAIL_VERIFICATION,
            PlanType.FREE,
            false,
            Set.of()
    );
  }

  private UserAccount activeUser() {
    return UserAccount.restore(
            UUID.fromString("019a19e6-fc96-7e7c-996e-86d7c3470002"),
            "User Name",
            "user@email.com",
            "encoded-password",
            UserStatus.ACTIVE,
            PlanType.FREE,
            true,
            Set.of()
    );
  }
}
