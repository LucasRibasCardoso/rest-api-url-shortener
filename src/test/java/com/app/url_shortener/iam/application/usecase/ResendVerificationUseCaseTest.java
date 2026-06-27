package com.app.url_shortener.iam.application.usecase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationOutboxPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationResendCooldownPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.usecase.impl.ResendVerificationUseCaseImpl;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.shared.ratelimit.exception.TooManyRequestsException;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Caso de Uso Reenvio de Verificação")
class ResendVerificationUseCaseTest {

  private static final String RESPONSE_MESSAGE =
      "Enviamos um novo código de verificação para o seu e-mail.";
  private static final Duration CODE_TTL = Duration.ofMinutes(10);
  private static final Duration SENDING_TIMEOUT = Duration.ofMinutes(1);
  private static final Duration RESEND_COOLDOWN = Duration.ofMinutes(2);

  @Mock private EmailVerificationOutboxPort emailVerificationEventPort;

  @Mock private UserAccountRepositoryPort userAccountRepositoryPort;

  @Mock private CheckAuthRateLimitPort checkAuthRateLimitPort;

  @Mock private EmailVerificationResendCooldownPort emailVerificationResendCooldownPort;

  private final EmailVerificationPolicy emailVerificationPolicy =
      new EmailVerificationPolicy(CODE_TTL, SENDING_TIMEOUT, RESEND_COOLDOWN);

  private ResendVerificationUseCaseImpl resendVerificationUseCase;

  @BeforeEach
  void setUp() {
    resendVerificationUseCase =
        new ResendVerificationUseCaseImpl(
            checkAuthRateLimitPort,
            userAccountRepositoryPort,
            emailVerificationEventPort,
            emailVerificationResendCooldownPort,
            emailVerificationPolicy);
  }

  @Nested
  @DisplayName("Execução do reenvio de verificação")
  class ExecuteTests {

    @Test
    @DisplayName("Deve publicar evento de reenvio quando o usuário estiver pendente")
    void shouldPublishResendEventWhenUserIsPending() {
      // 1. Arrange
      var command = new ResendVerificationCommand(" USER@EMAIL.COM ");
      var pendingUser = pendingUser();

      given(userAccountRepositoryPort.findByEmail("user@email.com"))
          .willReturn(Optional.of(pendingUser));
      given(emailVerificationResendCooldownPort.reserve(pendingUser.getEmail(), RESEND_COOLDOWN))
          .willReturn(true);

      // 2. Act
      var result = resendVerificationUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(RESPONSE_MESSAGE);

      InOrder inOrder =
          inOrder(
              checkAuthRateLimitPort,
              userAccountRepositoryPort,
              emailVerificationEventPort,
              emailVerificationResendCooldownPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification("user@email.com");
      inOrder.verify(userAccountRepositoryPort).findByEmail("user@email.com");
      inOrder
          .verify(emailVerificationEventPort)
          .publishEmailVerificationRequestedEvent(
              pendingUser.getId(), pendingUser.getEmail(), EmailDispatchReason.RESEND);
      inOrder
          .verify(emailVerificationResendCooldownPort)
          .reserve(pendingUser.getEmail(), RESEND_COOLDOWN);

      verifyNoMoreInteractions(
          checkAuthRateLimitPort,
          userAccountRepositoryPort,
          emailVerificationEventPort,
          emailVerificationResendCooldownPort);
    }

    @Test
    @DisplayName("Deve lançar TooManyRequests quando cooldown já estiver reservado")
    void shouldThrowTooManyRequestsWhenCooldownIsAlreadyReserved() {
      // 1. Arrange
      var command = new ResendVerificationCommand("user@email.com");
      var pendingUser = pendingUser();

      given(userAccountRepositoryPort.findByEmail(command.email()))
          .willReturn(Optional.of(pendingUser));
      given(emailVerificationResendCooldownPort.reserve(pendingUser.getEmail(), RESEND_COOLDOWN))
          .willReturn(false);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> resendVerificationUseCase.execute(command));

      // 3. Assert
      throwableAssert.isInstanceOf(TooManyRequestsException.class);

      InOrder inOrder =
          inOrder(
              checkAuthRateLimitPort,
              userAccountRepositoryPort,
              emailVerificationEventPort,
              emailVerificationResendCooldownPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification(command.email());
      inOrder.verify(userAccountRepositoryPort).findByEmail(command.email());
      inOrder
          .verify(emailVerificationEventPort)
          .publishEmailVerificationRequestedEvent(
              pendingUser.getId(), pendingUser.getEmail(), EmailDispatchReason.RESEND);
      inOrder
          .verify(emailVerificationResendCooldownPort)
          .reserve(pendingUser.getEmail(), RESEND_COOLDOWN);
      verifyNoMoreInteractions(
          checkAuthRateLimitPort,
          userAccountRepositoryPort,
          emailVerificationEventPort,
          emailVerificationResendCooldownPort);
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

      verifyNoInteractions(emailVerificationEventPort, emailVerificationResendCooldownPort);
      verifyNoMoreInteractions(checkAuthRateLimitPort, userAccountRepositoryPort);
    }

    @Test
    @DisplayName(
        "Deve retornar mensagem padrão sem publicar evento quando o usuário não estiver pendente")
    void shouldReturnDefaultMessageWithoutPublishingEventWhenUserIsNotPending() {
      // 1. Arrange
      var command = new ResendVerificationCommand("user@email.com");
      var activeUser = activeUser();

      given(userAccountRepositoryPort.findByEmail(command.email()))
          .willReturn(Optional.of(activeUser));

      // 2. Act
      var result = resendVerificationUseCase.execute(command);

      // 3. Assert
      assertThat(result.message()).isEqualTo(RESPONSE_MESSAGE);

      InOrder inOrder = inOrder(checkAuthRateLimitPort, userAccountRepositoryPort);
      inOrder.verify(checkAuthRateLimitPort).checkResendVerification(command.email());
      inOrder.verify(userAccountRepositoryPort).findByEmail(command.email());

      verifyNoInteractions(emailVerificationEventPort, emailVerificationResendCooldownPort);
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
        Set.of());
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
        Set.of());
  }
}
