package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.ResendVerificationCommand;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationOutboxPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationResendCooldownPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.ResendVerificationResult;
import com.app.url_shortener.iam.application.usecase.ResendVerificationUseCase;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.shared.ratelimit.exception.TooManyRequestsException;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ResendVerificationUseCaseImpl implements ResendVerificationUseCase {

  private static final String RESPONSE_MESSAGE =
      "Enviamos um novo código de verificação para o seu e-mail.";

  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationOutboxPort emailVerificationEventPort;
  private final EmailVerificationResendCooldownPort emailVerificationResendCooldownPort;
  private final EmailVerificationPolicy policy;

  @Override
  @Transactional
  public ResendVerificationResult execute(ResendVerificationCommand command) {
    checkAuthRateLimitPort.checkResendVerification(command.email());

    Optional<UserAccount> userAccountOptional =
        userAccountRepositoryPort.findByEmail(command.email()).filter(UserAccount::isPending);

    if (userAccountOptional.isPresent()) {
      UserAccount userAccount = userAccountOptional.get();
      emailVerificationEventPort.publishEmailVerificationRequestedEvent(
          userAccount.getId(), userAccount.getEmail(), EmailDispatchReason.RESEND);

      reserveResendCooldown(userAccount.getEmail());
    }

    return new ResendVerificationResult(RESPONSE_MESSAGE);
  }

  private void reserveResendCooldown(String email) {
    boolean reserved = emailVerificationResendCooldownPort.reserve(email, policy.resendCooldown());

    if (!reserved) {
      throw new TooManyRequestsException(policy.resendCooldown());
    }
  }
}
