package com.app.url_shortener.iam.application.service.impl;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailDispatchRepositoryPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.VerificationCodeProtectorPort;
import com.app.url_shortener.iam.application.service.EmailDispatchVerificationService;
import com.app.url_shortener.iam.application.service.model.PreparedEmailVerificationDispatch;
import com.app.url_shortener.iam.domain.enums.EmailDispatchPurpose;
import com.app.url_shortener.iam.domain.exception.auth.DuplicateEmailDispatchEventException;
import com.app.url_shortener.iam.domain.exception.auth.DuplicateOpenEmailVerificationTokenException;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationTokenNotFoundException;
import com.app.url_shortener.iam.domain.model.EmailDispatch;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class EmailDispatchVerificationServiceImpl implements EmailDispatchVerificationService {

  private final TransactionTemplate transactionTemplate;
  private final EmailVerificationPolicy emailVerificationPolicy;
  private final EmailDispatchRepositoryPort emailDispatchRepositoryPort;
  private final VerificationCodeProtectorPort verificationCodeProtectorPort;
  private final EmailVerificationTokenRepositoryPort verificationTokenRepositoryPort;

  @Override
  public PreparedEmailVerificationDispatch findOrCreate(EmailVerificationRequestedEvent event) {
    return emailDispatchRepositoryPort
        .findByEventId(event.eventId())
        .map(this::recoverExistingDispatch)
        .orElseGet(() -> createOrRecover(event));
  }

  private PreparedEmailVerificationDispatch recoverExistingDispatch(EmailDispatch dispatch) {
    EmailVerificationToken token =
        verificationTokenRepositoryPort
            .findById(dispatch.getVerificationTokenId())
            .orElseThrow(EmailVerificationTokenNotFoundException::new);
    return new PreparedEmailVerificationDispatch(dispatch, token);
  }

  private PreparedEmailVerificationDispatch createOrRecover(EmailVerificationRequestedEvent event) {
    try {
      return transactionTemplate.execute(status -> createTokenAndDispatch(event));

    } catch (DuplicateEmailDispatchEventException exception) {
      return recoverByDuplicatedDispatch(event, exception);

    } catch (DuplicateOpenEmailVerificationTokenException exception) {
      return recoverByDuplicatedOpenToken(event, exception);
    }
  }

  private PreparedEmailVerificationDispatch createTokenAndDispatch(
      EmailVerificationRequestedEvent event) {

    Instant now = Instant.now();
    verificationTokenRepositoryPort.revokeOpenByUserIdAndEmail(event.userId(), event.email(), now);

    var verificationCode = VerificationCode.generate();
    var verificationToken =
        EmailVerificationToken.create(
            event.userId(),
            event.email(),
            verificationCodeProtectorPort.hash(verificationCode),
            verificationCodeProtectorPort.encrypt(verificationCode),
            now.plus(emailVerificationPolicy.codeTtl()),
            now);
    var savedToken = verificationTokenRepositoryPort.save(verificationToken);

    var emailDispatch =
        EmailDispatch.create(
            event.eventId(),
            event.userId(),
            savedToken.getId(),
            event.email(),
            EmailDispatchPurpose.EMAIL_VERIFICATION,
            event.reason(),
            now);
    var savedDispatch = emailDispatchRepositoryPort.save(emailDispatch);

    return new PreparedEmailVerificationDispatch(savedDispatch, savedToken);
  }

  private PreparedEmailVerificationDispatch recoverByDuplicatedDispatch(
      EmailVerificationRequestedEvent event,
      DuplicateEmailDispatchEventException originalException) {

    return emailDispatchRepositoryPort
        .findByEventId(event.eventId())
        .map(this::recoverExistingDispatch)
        .orElseThrow(() -> originalException);
  }

  private PreparedEmailVerificationDispatch recoverByDuplicatedOpenToken(
      EmailVerificationRequestedEvent event,
      DuplicateOpenEmailVerificationTokenException originalException) {

    EmailVerificationToken token =
        verificationTokenRepositoryPort
            .findOpenByUserIdAndEmail(event.userId(), event.email())
            .orElseThrow(() -> originalException);

    try {
      EmailDispatch dispatch = createDispatchForEvent(event, token);
      return new PreparedEmailVerificationDispatch(dispatch, token);

    } catch (DuplicateEmailDispatchEventException exception) {
      return recoverByDuplicatedDispatch(event, exception);
    }
  }

  private EmailDispatch createDispatchForEvent(
      EmailVerificationRequestedEvent event, EmailVerificationToken token) {
    return transactionTemplate.execute(
        status -> {
          Instant now = Instant.now();
          EmailDispatch emailDispatch =
              EmailDispatch.create(
                  event.eventId(),
                  event.userId(),
                  token.getId(),
                  event.email(),
                  EmailDispatchPurpose.EMAIL_VERIFICATION,
                  event.reason(),
                  now);
          return emailDispatchRepositoryPort.save(emailDispatch);
        });
  }
}
