package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventPublisherPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.ResendVerificationResult;
import com.app.url_shortener.iam.application.usecase.ResendVerificationUseCase;
import com.app.url_shortener.iam.domain.model.UserAccount;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResendVerificationUseCaseImpl implements ResendVerificationUseCase {

  private static final String RESPONSE_MESSAGE = "Enviamos um novo código de verificação para o seu e-mail.";

  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationEventPublisherPort emailVerificationEventPublisherPort;

  @Override
  @Transactional
  public ResendVerificationResult execute(ResendVerificationCommand command) {
    checkAuthRateLimitPort.checkResendVerification(command.email());

    Optional<UserAccount> userAccountOptional = userAccountRepositoryPort.findByEmail(command.email());

    if (userAccountOptional.isEmpty() || !userAccountOptional.get().isPending()) {
      return new ResendVerificationResult(RESPONSE_MESSAGE);
    }

    emailVerificationEventPublisherPort.publish(EmailVerificationRequestedEvent.create(
            userAccountOptional.get().getId(),
            userAccountOptional.get().getEmail(),
            EmailVerificationReason.RESEND)
    );

    return new ResendVerificationResult(RESPONSE_MESSAGE);
  }
}
