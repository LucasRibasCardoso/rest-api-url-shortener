package com.app.url_shortener.iam.infrastructure.notification.event.listener;

import com.app.url_shortener.iam.application.event.EmailVerificationEvent;
import com.app.url_shortener.iam.infrastructure.notification.strategy.EmailSenderStrategy;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class EmailVerificationEventListener {

  private final EmailSenderStrategy emailSenderStrategy;

  @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
  public void onEmailVerificationRequested(EmailVerificationEvent event) {
    emailSenderStrategy.sendEmailVerificationCode(event.email(), event.code().value());
  }
}
