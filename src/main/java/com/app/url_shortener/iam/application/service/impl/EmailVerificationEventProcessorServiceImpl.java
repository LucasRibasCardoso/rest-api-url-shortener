package com.app.url_shortener.iam.application.service.impl;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailDispatchRepositoryPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.port.output.VerificationCodeProtectorPort;
import com.app.url_shortener.iam.application.port.output.model.EmailVerificationSendResult;
import com.app.url_shortener.iam.application.service.EmailDispatchVerificationService;
import com.app.url_shortener.iam.application.service.EmailVerificationEventProcessorService;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.domain.exception.auth.EmailDispatchAlreadyProcessingException;
import com.app.url_shortener.iam.domain.exception.auth.EmailDispatchStateConflictException;
import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.domain.model.UserAccount;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationEventProcessorServiceImpl
    implements EmailVerificationEventProcessorService {

  private final EmailVerificationPolicy emailVerificationPolicy;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationSenderPort emailVerificationSenderPort;
  private final EmailDispatchRepositoryPort emailDispatchRepositoryPort;
  private final VerificationCodeProtectorPort verificationCodeProtectorPort;
  private final EmailDispatchVerificationService emailDispatchVerificationService;

  @Override
  public void process(EmailVerificationRequestedEvent event) {
    userAccountRepositoryPort
        .findById(event.userId())
        .filter(user -> user.getEmail().equals(event.email()))
        .filter(UserAccount::isPending)
        .ifPresent(user -> processForPendingAccount(event));
  }

  private void processForPendingAccount(EmailVerificationRequestedEvent event) {
    var result = emailDispatchVerificationService.findOrCreate(event);
    var emailDispatch = result.dispatch();
    var emailVerificationToken = result.token();

    if (emailDispatch.isAccepted()) {
      log.info(
          "Evento de verificação de e-mail já foi enviado anteriormente. eventId={}, userId={}, email={}, reason={}",
          event.eventId(),
          event.userId(),
          event.email(),
          event.reason());
      return;
    }

    if (!emailVerificationToken.isActive(Instant.now())) {
      log.info(
          "Token de verificação de e-mail não está ativo. eventId={}, dispatchId={}, tokenId={}, userId={}",
          event.eventId(),
          emailDispatch.getId(),
          emailVerificationToken.getId(),
          event.userId());
      return;
    }

    tryMarkDispatchAsSending(emailDispatch);
    var sendResult = sendVerificationEmail(emailDispatch, emailVerificationToken);
    tryMarkDispatchAsAccepted(emailDispatch, sendResult.providerMessageId());
  }

  private void tryMarkDispatchAsSending(EmailDispatch emailDispatch) {
    Instant now = Instant.now();
    Instant staleSendingThreshold = now.minus(emailVerificationPolicy.sendingTimeout());

    boolean reserved =
        emailDispatchRepositoryPort.markAsSendingIfAvailable(
            emailDispatch.getId(), now, staleSendingThreshold);

    if (!reserved) {
      throw new EmailDispatchAlreadyProcessingException();
    }
  }

  private void tryMarkDispatchAsAccepted(EmailDispatch emailDispatch, String providerMessageId) {
    boolean accepted =
        emailDispatchRepositoryPort.markAsAccepted(
            emailDispatch.getId(), providerMessageId, Instant.now());

    if (!accepted) {
      throw new EmailDispatchStateConflictException();
    }
  }

  private EmailVerificationSendResult sendVerificationEmail(
      EmailDispatch dispatch, EmailVerificationToken token) {
    try {
      var verificationCode =
          verificationCodeProtectorPort.decrypt(token.getEncryptedCode()).value();
      return emailVerificationSenderPort.sendEmailVerificationCode(
          token.getEmail(), verificationCode);

    } catch (Exception exception) {
      emailDispatchRepositoryPort.markAsFailed(
          dispatch.getId(),
          IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED.name(),
          exception.getClass().getSimpleName(),
          Instant.now());
      throw exception;
    }
  }
}
