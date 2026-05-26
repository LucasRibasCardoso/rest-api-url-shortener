package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.ResendVerificationResult;
import com.app.url_shortener.iam.application.usecase.ResendVerificationUseCase;
import com.app.url_shortener.iam.domain.event.EmailVerificationEvent;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ResendVerificationUseCaseImpl implements ResendVerificationUseCase {

  private static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(10);
  private static final String RESPONSE_MESSAGE = "Enviamos um novo código de verificação para o seu e-mail.";

  private final ApplicationEventPublisher eventPublisher;
  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenPort emailVerificationTokenStore;

  @Override
  @Transactional(readOnly = true)
  public ResendVerificationResult execute(ResendVerificationCommand command) {
    checkAuthRateLimitPort.checkResendVerification(command.email());

    Optional<UserAccount> userOpt = userAccountRepositoryPort.findByEmail(command.email());

    if (userOpt.isEmpty() || !userOpt.get().isPending()) {
      return new ResendVerificationResult(RESPONSE_MESSAGE);
    }

    UserAccount userAccount = userOpt.get();
    VerificationCode verificationCode = VerificationCode.generate();
    EmailVerificationToken newToken = EmailVerificationToken.create(
            userAccount.getId(),
            userAccount.getEmail(),
            verificationCode,
            Instant.now().plus(VERIFICATION_CODE_TTL));

    emailVerificationTokenStore.store(newToken, VERIFICATION_CODE_TTL);
    eventPublisher.publishEvent(new EmailVerificationEvent(
            userAccount.getId(),
            userAccount.getEmail(),
            verificationCode));

    return new ResendVerificationResult(RESPONSE_MESSAGE);
  }
}
