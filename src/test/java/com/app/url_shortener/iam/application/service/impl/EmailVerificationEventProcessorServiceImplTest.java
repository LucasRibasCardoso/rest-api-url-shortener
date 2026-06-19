package com.app.url_shortener.iam.application.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailDispatchRepositoryPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.port.output.VerificationCodeProtectorPort;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationSendResult;
import com.app.url_shortener.iam.application.service.EmailDispatchVerificationService;
import com.app.url_shortener.iam.application.service.model.PreparedEmailVerificationDispatch;
import com.app.url_shortener.iam.domain.enums.EmailDispatchPurpose;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.domain.exception.auth.EmailDispatchAlreadyProcessingException;
import com.app.url_shortener.iam.domain.exception.auth.EmailDispatchStateConflictException;
import com.app.url_shortener.iam.domain.exception.auth.VerificationCodeProtectionException;
import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Processador de Evento de Verificação de Email")
class EmailVerificationEventProcessorServiceImplTest {

  private static final UUID EVENT_ID = UUID.fromString("019a2d73-4e3c-789c-8791-2f46e2700101");
  private static final UUID USER_ID = UUID.fromString("019a2d73-4e3c-789c-8791-2f46e2700102");
  private static final UUID TOKEN_ID = UUID.fromString("019a2d73-4e3c-789c-8791-2f46e2700103");
  private static final UUID DISPATCH_ID = UUID.fromString("019a2d73-4e3c-789c-8791-2f46e2700104");
  private static final String EMAIL = "user@email.com";
  private static final Instant CREATED_AT = Instant.parse("2026-06-18T10:00:00Z");

  @Mock private EmailVerificationPolicy emailVerificationPolicy;

  @Mock private UserAccountRepositoryPort userAccountRepositoryPort;

  @Mock private EmailVerificationSenderPort emailVerificationSenderPort;

  @Mock private EmailDispatchRepositoryPort emailDispatchRepositoryPort;

  @Mock private VerificationCodeProtectorPort verificationCodeProtectorPort;

  @Mock private EmailDispatchVerificationService emailDispatchVerificationService;

  @InjectMocks private EmailVerificationEventProcessorServiceImpl service;

  @Nested
  @DisplayName("Processamento")
  class ProcessTests {

    @Test
    @DisplayName("Deve ignorar evento quando usuário não existir")
    void shouldIgnoreEventWhenUserDoesNotExist() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId())).willReturn(Optional.empty());

      // 2. Act
      service.process(event);

      // 3. Assert
      verify(userAccountRepositoryPort).findById(event.userId());
      verifyNoMoreInteractions(userAccountRepositoryPort);
      verifyNoInteractions(
          emailVerificationPolicy,
          emailVerificationSenderPort,
          emailDispatchRepositoryPort,
          verificationCodeProtectorPort,
          emailDispatchVerificationService);
    }

    @Test
    @DisplayName("Deve ignorar evento quando email do usuário divergir")
    void shouldIgnoreEventWhenUserEmailDoesNotMatch() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(pendingUser("other@email.com")));

      // 2. Act
      service.process(event);

      // 3. Assert
      verify(userAccountRepositoryPort).findById(event.userId());
      verifyNoMoreInteractions(userAccountRepositoryPort);
      verifyNoInteractions(
          emailVerificationPolicy,
          emailVerificationSenderPort,
          emailDispatchRepositoryPort,
          verificationCodeProtectorPort,
          emailDispatchVerificationService);
    }

    @Test
    @DisplayName("Deve ignorar evento quando conta não estiver pendente")
    void shouldIgnoreEventWhenUserIsNotPending() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(activeUser()));

      // 2. Act
      service.process(event);

      // 3. Assert
      verify(userAccountRepositoryPort).findById(event.userId());
      verifyNoMoreInteractions(userAccountRepositoryPort);
      verifyNoInteractions(
          emailVerificationPolicy,
          emailVerificationSenderPort,
          emailDispatchRepositoryPort,
          verificationCodeProtectorPort,
          emailDispatchVerificationService);
    }

    @Test
    @DisplayName("Deve enviar email e marcar dispatch como aceito")
    void shouldSendEmailAndMarkDispatchAsAccepted() {
      // 1. Arrange
      var event = event();
      var dispatch = dispatch(EmailDispatchStatus.PENDING);
      var token = activeToken();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(pendingUser(EMAIL)));
      given(emailDispatchVerificationService.findOrCreate(event))
          .willReturn(new PreparedEmailVerificationDispatch(dispatch, token));
      given(emailVerificationPolicy.sendingTimeout()).willReturn(Duration.ofMinutes(1));
      given(emailDispatchRepositoryPort.markAsSendingIfAvailable(eq(DISPATCH_ID), any(), any()))
          .willReturn(true);
      given(verificationCodeProtectorPort.decrypt(token.getEncryptedCode()))
          .willReturn(VerificationCode.of("123456"));
      given(emailVerificationSenderPort.sendEmailVerificationCode(EMAIL, "123456"))
          .willReturn(new EmailVerificationSendResult("provider-message-id"));
      given(emailDispatchRepositoryPort.markAsAccepted(eq(DISPATCH_ID), eq("provider-message-id"), any()))
          .willReturn(true);

      // 2. Act
      service.process(event);

      // 3. Assert
      verify(userAccountRepositoryPort).findById(event.userId());
      verify(emailDispatchVerificationService).findOrCreate(event);
      verify(emailDispatchRepositoryPort).markAsSendingIfAvailable(eq(DISPATCH_ID), any(), any());
      verify(verificationCodeProtectorPort).decrypt(token.getEncryptedCode());
      verify(emailVerificationSenderPort).sendEmailVerificationCode(EMAIL, "123456");
      verify(emailDispatchRepositoryPort).markAsAccepted(eq(DISPATCH_ID), eq("provider-message-id"), any());
      verifyNoMoreInteractions(
          userAccountRepositoryPort,
          emailVerificationPolicy,
          emailVerificationSenderPort,
          emailDispatchRepositoryPort,
          verificationCodeProtectorPort,
          emailDispatchVerificationService);
    }

    @Test
    @DisplayName("Deve ignorar dispatch já aceito")
    void shouldIgnoreAcceptedDispatch() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(pendingUser(EMAIL)));
      given(emailDispatchVerificationService.findOrCreate(event))
          .willReturn(new PreparedEmailVerificationDispatch(acceptedDispatch(), activeToken()));

      // 2. Act
      service.process(event);

      // 3. Assert
      verify(userAccountRepositoryPort).findById(event.userId());
      verify(emailDispatchVerificationService).findOrCreate(event);
      verifyNoMoreInteractions(userAccountRepositoryPort, emailDispatchVerificationService);
      verifyNoInteractions(
          emailVerificationPolicy,
          emailVerificationSenderPort,
          emailDispatchRepositoryPort,
          verificationCodeProtectorPort);
    }

    @Test
    @DisplayName("Deve ignorar dispatch quando token não estiver ativo")
    void shouldIgnoreDispatchWhenTokenIsNotActive() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(pendingUser(EMAIL)));
      given(emailDispatchVerificationService.findOrCreate(event))
          .willReturn(new PreparedEmailVerificationDispatch(dispatch(EmailDispatchStatus.PENDING), expiredToken()));

      // 2. Act
      service.process(event);

      // 3. Assert
      verify(userAccountRepositoryPort).findById(event.userId());
      verify(emailDispatchVerificationService).findOrCreate(event);
      verifyNoMoreInteractions(userAccountRepositoryPort, emailDispatchVerificationService);
      verifyNoInteractions(
          emailVerificationPolicy,
          emailVerificationSenderPort,
          emailDispatchRepositoryPort,
          verificationCodeProtectorPort);
    }

    @Test
    @DisplayName("Deve lançar conflito quando dispatch não puder ser reservado")
    void shouldThrowConflictWhenDispatchCannotBeReserved() {
      // 1. Arrange
      var event = event();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(pendingUser(EMAIL)));
      given(emailDispatchVerificationService.findOrCreate(event))
          .willReturn(new PreparedEmailVerificationDispatch(dispatch(EmailDispatchStatus.PENDING), activeToken()));
      given(emailVerificationPolicy.sendingTimeout()).willReturn(Duration.ofMinutes(1));
      given(emailDispatchRepositoryPort.markAsSendingIfAvailable(eq(DISPATCH_ID), any(), any()))
          .willReturn(false);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.process(event));

      // 3. Assert
      throwableAssert.isInstanceOf(EmailDispatchAlreadyProcessingException.class);
      verify(emailDispatchRepositoryPort).markAsSendingIfAvailable(eq(DISPATCH_ID), any(), any());
      verify(emailVerificationSenderPort, never()).sendEmailVerificationCode(any(), any());
    }

    @Test
    @DisplayName("Deve marcar dispatch como falho e relançar quando envio falhar")
    void shouldMarkDispatchAsFailedAndRethrowWhenSendingFails() {
      // 1. Arrange
      var event = event();
      var dispatch = dispatch(EmailDispatchStatus.PENDING);
      var token = activeToken();
      var sendFailure = new IllegalStateException("provider unavailable");
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(pendingUser(EMAIL)));
      given(emailDispatchVerificationService.findOrCreate(event))
          .willReturn(new PreparedEmailVerificationDispatch(dispatch, token));
      given(emailVerificationPolicy.sendingTimeout()).willReturn(Duration.ofMinutes(1));
      given(emailDispatchRepositoryPort.markAsSendingIfAvailable(eq(DISPATCH_ID), any(), any()))
          .willReturn(true);
      given(verificationCodeProtectorPort.decrypt(token.getEncryptedCode()))
          .willReturn(VerificationCode.of("123456"));
      given(emailVerificationSenderPort.sendEmailVerificationCode(EMAIL, "123456"))
          .willThrow(sendFailure);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.process(event));

      // 3. Assert
      throwableAssert.isSameAs(sendFailure);
      verify(emailDispatchRepositoryPort)
          .markAsFailed(
              eq(DISPATCH_ID),
              eq(IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED.name()),
              eq(IllegalStateException.class.getSimpleName()),
              any());
      verify(emailDispatchRepositoryPort, never()).markAsAccepted(any(), any(), any());
    }

    @Test
    @DisplayName("Deve marcar dispatch como falho e relançar quando descriptografia falhar")
    void shouldMarkDispatchAsFailedAndRethrowWhenDecryptionFails() {
      // 1. Arrange
      var event = event();
      var dispatch = dispatch(EmailDispatchStatus.PENDING);
      var token = activeToken();
      var decryptionFailure =
          new VerificationCodeProtectionException(IamErrorCode.AUTH_VERIFICATION_CODE_DECRYPT_FAILED);
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(pendingUser(EMAIL)));
      given(emailDispatchVerificationService.findOrCreate(event))
          .willReturn(new PreparedEmailVerificationDispatch(dispatch, token));
      given(emailVerificationPolicy.sendingTimeout()).willReturn(Duration.ofMinutes(1));
      given(emailDispatchRepositoryPort.markAsSendingIfAvailable(eq(DISPATCH_ID), any(), any()))
          .willReturn(true);
      given(verificationCodeProtectorPort.decrypt(token.getEncryptedCode()))
          .willThrow(decryptionFailure);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.process(event));

      // 3. Assert
      throwableAssert.isSameAs(decryptionFailure);
      verify(emailDispatchRepositoryPort)
          .markAsFailed(
              eq(DISPATCH_ID),
              eq(IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED.name()),
              eq(VerificationCodeProtectionException.class.getSimpleName()),
              any());
      verify(emailVerificationSenderPort, never()).sendEmailVerificationCode(any(), any());
      verify(emailDispatchRepositoryPort, never()).markAsAccepted(any(), any(), any());
    }

    @Test
    @DisplayName("Deve lançar conflito quando dispatch não puder ser aceito após envio")
    void shouldThrowConflictWhenDispatchCannotBeAcceptedAfterSending() {
      // 1. Arrange
      var event = event();
      var dispatch = dispatch(EmailDispatchStatus.PENDING);
      var token = activeToken();
      given(userAccountRepositoryPort.findById(event.userId()))
          .willReturn(Optional.of(pendingUser(EMAIL)));
      given(emailDispatchVerificationService.findOrCreate(event))
          .willReturn(new PreparedEmailVerificationDispatch(dispatch, token));
      given(emailVerificationPolicy.sendingTimeout()).willReturn(Duration.ofMinutes(1));
      given(emailDispatchRepositoryPort.markAsSendingIfAvailable(eq(DISPATCH_ID), any(), any()))
          .willReturn(true);
      given(verificationCodeProtectorPort.decrypt(token.getEncryptedCode()))
          .willReturn(VerificationCode.of("123456"));
      given(emailVerificationSenderPort.sendEmailVerificationCode(EMAIL, "123456"))
          .willReturn(new EmailVerificationSendResult("provider-message-id"));
      given(emailDispatchRepositoryPort.markAsAccepted(eq(DISPATCH_ID), eq("provider-message-id"), any()))
          .willReturn(false);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.process(event));

      // 3. Assert
      throwableAssert.isInstanceOf(EmailDispatchStateConflictException.class);
      verify(emailDispatchRepositoryPort).markAsAccepted(eq(DISPATCH_ID), eq("provider-message-id"), any());
    }
  }

  private EmailVerificationRequestedEvent event() {
    return new EmailVerificationRequestedEvent(
        EVENT_ID, USER_ID, EMAIL, EmailDispatchReason.REGISTER, CREATED_AT);
  }

  private UserAccount pendingUser(String email) {
    return UserAccount.restore(
        USER_ID,
        "User",
        email,
        "password-hash",
        UserStatus.PENDING_EMAIL_VERIFICATION,
        PlanType.FREE,
        false,
        Set.of());
  }

  private UserAccount activeUser() {
    return UserAccount.restore(
        USER_ID,
        "User",
        EMAIL,
        "password-hash",
        UserStatus.ACTIVE,
        PlanType.FREE,
        true,
        Set.of());
  }

  private EmailDispatch dispatch(EmailDispatchStatus status) {
    return EmailDispatch.restore(
        DISPATCH_ID,
        EVENT_ID,
        USER_ID,
        TOKEN_ID,
        EMAIL,
        EmailDispatchPurpose.EMAIL_VERIFICATION,
        EmailDispatchReason.REGISTER,
        status,
        null,
        status == EmailDispatchStatus.PENDING ? 0 : 1,
        status == EmailDispatchStatus.SENDING ? CREATED_AT.plusSeconds(1) : null,
        null,
        null,
        null,
        null,
        CREATED_AT,
        CREATED_AT.plusSeconds(1));
  }

  private EmailDispatch acceptedDispatch() {
    return EmailDispatch.restore(
        DISPATCH_ID,
        EVENT_ID,
        USER_ID,
        TOKEN_ID,
        EMAIL,
        EmailDispatchPurpose.EMAIL_VERIFICATION,
        EmailDispatchReason.REGISTER,
        EmailDispatchStatus.ACCEPTED,
        "provider-message-id",
        1,
        CREATED_AT.plusSeconds(1),
        CREATED_AT.plusSeconds(2),
        null,
        null,
        null,
        CREATED_AT,
        CREATED_AT.plusSeconds(2));
  }

  private EmailVerificationToken activeToken() {
    var now = Instant.now();
    return token(now.minusSeconds(10), now.plusSeconds(600), null, null);
  }

  private EmailVerificationToken expiredToken() {
    return token(CREATED_AT, CREATED_AT.plusSeconds(10), null, null);
  }

  private EmailVerificationToken token(
      Instant createdAt, Instant expiresAt, Instant consumedAt, Instant revokedAt) {
    return EmailVerificationToken.restore(
        TOKEN_ID,
        USER_ID,
        EMAIL,
        "verification-code-hash",
        "encrypted-code",
        expiresAt,
        consumedAt,
        revokedAt,
        0,
        null,
        createdAt,
        createdAt);
  }
}
