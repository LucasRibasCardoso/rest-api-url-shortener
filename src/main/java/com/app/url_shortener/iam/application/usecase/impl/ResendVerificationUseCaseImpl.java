package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventPublisherPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.ResendVerificationResult;
import com.app.url_shortener.iam.application.usecase.ResendVerificationUseCase;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResendVerificationUseCaseImpl implements ResendVerificationUseCase {

  private static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(10);
  private static final String RESPONSE_MESSAGE = "Enviamos um novo código de verificação para o seu e-mail.";

  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenStorePort emailVerificationTokenStorePort;
  private final EmailVerificationEventPublisherPort emailVerificationEventPublisherPort;

  @Override
  @Transactional
  public ResendVerificationResult execute(ResendVerificationCommand command) {
    String email = command.email();
    checkAuthRateLimitPort.checkResendVerification(email);

    Optional<UserAccount> userAccountOptional = userAccountRepositoryPort.findByEmail(email);

    if (userAccountOptional.isEmpty() || !userAccountOptional.get().isPending()) {
      return new ResendVerificationResult(RESPONSE_MESSAGE);
    }

    UserAccount userAccount = userAccountOptional.get();
    VerificationCode verificationCode = VerificationCode.generate();
    EmailVerificationToken emailVerificationToken =
        EmailVerificationToken.create(
            userAccount.getId(),
            userAccount.getEmail(),
            verificationCode,
            Instant.now().plus(VERIFICATION_CODE_TTL));

    emailVerificationTokenStorePort.store(emailVerificationToken, VERIFICATION_CODE_TTL);
    emailVerificationEventPublisherPort.publish(
        EmailVerificationRequestedEvent.create(
            userAccount.getId(), userAccount.getEmail(), verificationCode));

    return new ResendVerificationResult(RESPONSE_MESSAGE);
  }
}
