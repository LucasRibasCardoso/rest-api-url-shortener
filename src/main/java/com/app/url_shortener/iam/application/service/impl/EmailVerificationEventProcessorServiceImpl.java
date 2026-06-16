package com.app.url_shortener.iam.application.service.impl;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.EmailVerificationSenderPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.service.EmailVerificationEventProcessorService;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationEventProcessorServiceImpl implements EmailVerificationEventProcessorService {

  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenStorePort emailVerificationTokenStorePort;
  private final EmailVerificationSenderPort emailVerificationSenderPort;
  private final EmailVerificationPolicy emailVerificationPolicy;

  @Override
  public void process(EmailVerificationRequestedEvent event) {
    userAccountRepositoryPort
        .findById(event.userId())
        .filter(user -> user.getEmail().equals(event.email()))
        .filter(UserAccount::isPending)
        .ifPresentOrElse(
            this::sendVerificationEmail,
            () ->
                log.info(
                    "Evento de verificação de e-mail ignorado por usuário inexistente, e-mail divergente ou status inválido. eventId={}, userId={}, email={}, reason={}",
                    event.eventId(),
                    event.userId(),
                    event.email(),
                    event.reason()));
  }

  private void sendVerificationEmail(UserAccount userAccount) {
    var ttl = emailVerificationPolicy.codeTtl();
    var expiresAt = Instant.now().plus(ttl);
    var verificationCode = VerificationCode.generate();

    var emailVerificationToken = EmailVerificationToken.create(
            userAccount.getId(),
            userAccount.getEmail(),
            verificationCode,
            expiresAt);

    emailVerificationTokenStorePort.store(emailVerificationToken, ttl);
    emailVerificationSenderPort.sendEmailVerificationCode(emailVerificationToken.email(), verificationCode.value());
  }
}
