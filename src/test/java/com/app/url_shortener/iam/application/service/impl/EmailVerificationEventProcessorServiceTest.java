package com.app.url_shortener.iam.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.service.EmailVerificationEventProcessorService;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Processador de Evento de Verificação de Email")
class EmailVerificationEventProcessorServiceTest {

  private static final Duration CODE_TTL = Duration.ofMinutes(10);
  private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(4);

  @Mock
  private UserAccountRepositoryPort userAccountRepositoryPort;

  @Mock
  private EmailVerificationTokenStorePort emailVerificationTokenStorePort;

  @Mock
  private EmailVerificationSenderPort emailVerificationSenderPort;

  private EmailVerificationEventProcessorService processorService;

  @BeforeEach
  void setUp() {
    var policy = new EmailVerificationPolicy(CODE_TTL, IDEMPOTENCY_TTL);
    processorService =
        new EmailVerificationEventProcessorServiceImpl(
            userAccountRepositoryPort,
            emailVerificationTokenStorePort,
            emailVerificationSenderPort,
            policy);
  }

  @Nested
  @DisplayName("Processamento")
  class ProcessTests {

    @Test
    @DisplayName("Deve gerar, armazenar e enviar código para usuário pendente")
    void shouldGenerateStoreAndSendCodeForPendingUser() {
      // 1. Arrange
      var event = event();
      var user = user(UserStatus.PENDING_EMAIL_VERIFICATION, event.email());
      given(userAccountRepositoryPort.findById(event.userId())).willReturn(Optional.of(user));

      // 2. Act
      processorService.process(event);

      // 3. Assert
      var tokenCaptor = ArgumentCaptor.forClass(EmailVerificationToken.class);
      verify(emailVerificationTokenStorePort).store(tokenCaptor.capture(), eq(CODE_TTL));
      var token = tokenCaptor.getValue();
      assertThat(token.userId()).isEqualTo(event.userId());
      assertThat(token.email()).isEqualTo(event.email());
      assertThat(token.code().value()).matches("\\d{6}");
      assertThat(token.expiresAt()).isAfter(Instant.now().plus(CODE_TTL).minusSeconds(5));
      verify(emailVerificationSenderPort).sendEmailVerificationCode(event.email(), token.code().value());
      verifyNoMoreInteractions(
          userAccountRepositoryPort, emailVerificationTokenStorePort, emailVerificationSenderPort);
    }

    @Test
    @DisplayName("Deve ignorar evento quando usuário não existir")
    void shouldIgnoreEventWhenUserDoesNotExist() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId())).willReturn(Optional.empty());

      // 2. Act
      processorService.process(event);

      // 3. Assert
      verifyNoInteractions(emailVerificationTokenStorePort, emailVerificationSenderPort);
      verifyNoMoreInteractions(userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve ignorar evento quando email divergir")
    void shouldIgnoreEventWhenEmailDiffers() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(user(UserStatus.PENDING_EMAIL_VERIFICATION, "other@email.com")));

      // 2. Act
      processorService.process(event);

      // 3. Assert
      verifyNoInteractions(emailVerificationTokenStorePort, emailVerificationSenderPort);
      verifyNoMoreInteractions(userAccountRepositoryPort);
    }

    @Test
    @DisplayName("Deve ignorar evento quando usuário não estiver pendente")
    void shouldIgnoreEventWhenUserIsNotPending() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(user(UserStatus.ACTIVE, event.email())));

      // 2. Act
      processorService.process(event);

      // 3. Assert
      verifyNoInteractions(emailVerificationTokenStorePort, emailVerificationSenderPort);
      verifyNoMoreInteractions(userAccountRepositoryPort);
    }
  }

  private EmailVerificationRequestedEvent event() {
    return new EmailVerificationRequestedEvent(
        UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100"),
        UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101"),
        "user@email.com",
        EmailVerificationReason.REGISTER,
        Instant.parse("2026-06-09T12:00:00Z"));
  }

  private UserAccount user(UserStatus status, String email) {
    return UserAccount.restore(
        UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101"),
        "User Name",
        email,
        "encoded-password",
        status,
        PlanType.FREE,
        status == UserStatus.ACTIVE,
        Set.of());
  }
}
