package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.VerifyEmailCommand;
import com.app.url_shortener.iam.application.port.output.*;
import com.app.url_shortener.iam.application.result.VerifyEmailResult;
import com.app.url_shortener.iam.application.usecase.VerifyEmailUseCase;
import com.app.url_shortener.iam.domain.exception.auth.InvalidOrExpiredEmailVerificationCodeException;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerifyEmailUseCaseImpl implements VerifyEmailUseCase {

  private static final String SUCCESS_MESSAGE =
      "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";

  private final RoleRepositoryPort roleRepositoryPort;
  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final VerificationCodeProtectorPort verificationCodeProtectorPort;
  private final EmailVerificationTokenRepositoryPort emailVerificationTokenRepositoryPort;

  @Override
  @Transactional
  public VerifyEmailResult execute(VerifyEmailCommand command) {
    checkAuthRateLimitPort.checkVerifyEmail(command.email());

    UserAccount userAccount = fetchUserAccountByEmail(command.email());
    consumeToken(userAccount, command.code());
    verifyAccountEmail(userAccount);

    return new VerifyEmailResult(SUCCESS_MESSAGE);
  }

  private UserAccount fetchUserAccountByEmail(String email) {
    return userAccountRepositoryPort
        .findByEmailWithRoles(email)
        .orElseThrow(InvalidOrExpiredEmailVerificationCodeException::new);
  }

  private void consumeToken(UserAccount userAccount, VerificationCode verificationCode) {
    var now = Instant.now();

    EmailVerificationToken token =
        emailVerificationTokenRepositoryPort
            .findActiveByUserIdAndEmail(userAccount.getId(), userAccount.getEmail(), now)
            .orElseThrow(InvalidOrExpiredEmailVerificationCodeException::new);

    if (!verificationCodeProtectorPort.matches(verificationCode, token.getHashedCode())) {
      emailVerificationTokenRepositoryPort.registerFailedAttempt(token.getId(), now);
      throw new InvalidOrExpiredEmailVerificationCodeException();
    }

    boolean consumed = emailVerificationTokenRepositoryPort.consumeIfActive(token.getId(), now);
    if (!consumed) {
      throw new InvalidOrExpiredEmailVerificationCodeException();
    }
  }

  private void verifyAccountEmail(UserAccount userAccount) {
    Role defaultRole = roleRepositoryPort.findDefaultRole();
    userAccount.verifyEmail(defaultRole);
    userAccountRepositoryPort.save(userAccount);
  }
}
