package com.app.url_shortener.iam.infrastructure.notification.event;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import io.awspring.cloud.sqs.operations.SqsAsyncOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
public class EmailVerificationEventListener {

  private final String emailVerificationEventsQueue;
  private final SqsAsyncOperations sqsAsyncOperations;

  public EmailVerificationEventListener(
      @Value("${app.aws.sqs.email-verification-events-queue}")
      String emailVerificationEventsQueue,
      SqsAsyncOperations sqsAsyncOperations) {
    this.emailVerificationEventsQueue = emailVerificationEventsQueue;
    this.sqsAsyncOperations = sqsAsyncOperations;
  }

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEmailVerificationRequested(EmailVerificationRequestedEvent event) {
    try {
      sqsAsyncOperations
          .sendAsync(emailVerificationEventsQueue, event)
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  log.warn(
                      "Falha ao publicar evento de verificação de email. eventId={}, userId={}, reason={}, queue={}",
                      event.eventId(),
                      event.userId(),
                      event.reason(),
                      emailVerificationEventsQueue,
                      exception);
                  return;
                }

                log.debug(
                    "Evento de verificação de email publicado. eventId={}, userId={}, reason={}, queue={}, messageId={}",
                    event.eventId(),
                    event.userId(),
                    event.reason(),
                    emailVerificationEventsQueue,
                    result.messageId());
              });
    } catch (RuntimeException exception) {
      log.warn(
          "Falha imediata ao publicar evento de verificação de email. eventId={}, userId={}, reason={}, queue={}",
          event.eventId(),
          event.userId(),
          event.reason(),
          emailVerificationEventsQueue,
          exception);
    }
  }
}
