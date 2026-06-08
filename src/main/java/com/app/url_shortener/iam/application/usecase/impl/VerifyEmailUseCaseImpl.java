package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.VerifyEmailCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.application.port.output.RoleRepositoryPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.VerifyEmailResult;
import com.app.url_shortener.iam.application.usecase.VerifyEmailUseCase;
import com.app.url_shortener.iam.domain.exception.auth.InvalidOrExpiredEmailVerificationCodeException;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class VerifyEmailUseCaseImpl implements VerifyEmailUseCase {

  private static final String SUCCESS_MESSAGE =
      "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";

  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final EmailVerificationTokenStorePort emailVerificationTokenStorePort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final RoleRepositoryPort roleRepositoryPort;

  @Override
  @Transactional
  public VerifyEmailResult execute(VerifyEmailCommand command) {
    String email = command.email();
    checkAuthRateLimitPort.checkVerifyEmail(email);
    EmailVerificationToken emailVerificationToken = consumeAndValidateToken(email, command.code());
    verifyAccountEmail(email, emailVerificationToken);

    return new VerifyEmailResult(SUCCESS_MESSAGE);
  }

  private EmailVerificationToken consumeAndValidateToken(String email, VerificationCode verificationCode) {
    EmailVerificationToken emailVerificationToken =
        emailVerificationTokenStorePort
            .consumeByEmailAndCode(email, verificationCode)
            .orElseThrow(InvalidOrExpiredEmailVerificationCodeException::new);

    if (emailVerificationToken.isExpired()) {
      throw new InvalidOrExpiredEmailVerificationCodeException();
    }

    return emailVerificationToken;
  }

  private void verifyAccountEmail(String email, EmailVerificationToken emailVerificationToken) {
    UserAccount userAccount =
        userAccountRepositoryPort
            .findByEmailWithRoles(email)
            .orElseThrow(InvalidOrExpiredEmailVerificationCodeException::new);

    if (!emailVerificationToken.userId().equals(userAccount.getId())) {
      throw new InvalidOrExpiredEmailVerificationCodeException();
    }

    Role defaultRole = roleRepositoryPort.findDefaultRole();
    userAccount.verifyEmail(defaultRole);
    userAccountRepositoryPort.save(userAccount);
  }
}
