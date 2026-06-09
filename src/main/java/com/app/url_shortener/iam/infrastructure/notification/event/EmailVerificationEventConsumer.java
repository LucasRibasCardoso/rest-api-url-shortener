package com.app.url_shortener.iam.infrastructure.notification.event;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventIdempotencyPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import com.app.url_shortener.iam.infrastructure.notification.strategy.EmailSenderStrategy;
import io.awspring.cloud.sqs.annotation.SqsListener;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class EmailVerificationEventConsumer {

  private static final Duration IDEMPOTENCY_TTL = Duration.ofDays(4);
  private static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(10);

  private final EmailVerificationEventIdempotencyPort idempotencyPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenStorePort emailVerificationTokenStorePort;
  private final EmailSenderStrategy emailSenderStrategy;

  @SqsListener("${app.aws.sqs.email-verification-events-queue}")
  public void consume(EmailVerificationRequestedEvent event) {
    if (!idempotencyPort.tryMarkAsProcessed(event.eventId(), IDEMPOTENCY_TTL)) {
      log.debug("Evento de verificação de email duplicado ignorado. eventId={}", event.eventId());
      return;
    }

    try {
      process(event);
    } catch (RuntimeException exception) {
      removeProcessedMark(event, exception);
      throw exception;
    }
  }

  private void process(EmailVerificationRequestedEvent event) {
    var userAccountOptional = userAccountRepositoryPort.findById(event.userId());

    if (userAccountOptional.isEmpty()) {
      log.debug("Evento de verificação ignorado para usuário inexistente. eventId={}", event.eventId());
      return;
    }

    var userAccount = userAccountOptional.get();
    if (!userAccount.getEmail().equals(event.email())) {
      log.warn(
          "Evento de verificação ignorado por divergência de email. eventId={}, userId={}",
          event.eventId(),
          event.userId());
      return;
    }

    if (userAccount.getStatus() != UserStatus.PENDING_EMAIL_VERIFICATION) {
      log.debug(
          "Evento de verificação ignorado para usuário não pendente. eventId={}, userId={}, status={}",
          event.eventId(),
          event.userId(),
          userAccount.getStatus());
      return;
    }

    VerificationCode verificationCode = VerificationCode.generate();
    EmailVerificationToken token =
        EmailVerificationToken.create(
            userAccount.getId(),
            userAccount.getEmail(),
            verificationCode,
            Instant.now().plus(VERIFICATION_CODE_TTL));

    emailVerificationTokenStorePort.store(token, VERIFICATION_CODE_TTL);
    emailSenderStrategy.sendEmailVerificationCode(userAccount.getEmail(), verificationCode.value());
  }

  private void removeProcessedMark(EmailVerificationRequestedEvent event, RuntimeException processingException) {
    try {
      idempotencyPort.removeProcessedMark(event.eventId());
    } catch (RuntimeException cleanupException) {
      processingException.addSuppressed(cleanupException);
      log.error(
          "Falha ao remover marca de idempotência após erro no processamento. eventId={}",
          event.eventId(),
          cleanupException);
    }
  }
}
