package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.ResendVerificationResult;
import com.app.url_shortener.iam.application.usecase.ResendVerificationUseCase;
import com.app.url_shortener.iam.domain.model.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResendVerificationUseCaseImpl implements ResendVerificationUseCase {

  private static final String RESPONSE_MESSAGE = "Enviamos um novo código de verificação para o seu e-mail.";

  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationEventPort emailVerificationEventPort;

  @Override
  @Transactional
  public ResendVerificationResult execute(ResendVerificationCommand command) {
    checkAuthRateLimitPort.checkResendVerification(command.email());

    userAccountRepositoryPort
        .findByEmail(command.email())
        .filter(UserAccount::isPending)
        .ifPresent(userAccount ->
                emailVerificationEventPort.publishEmailVerificationRequestedEvent(
                    userAccount.getId(),
                    userAccount.getEmail(),
                    EmailVerificationReason.RESEND
            ));

    return new ResendVerificationResult(RESPONSE_MESSAGE);
  }
}
