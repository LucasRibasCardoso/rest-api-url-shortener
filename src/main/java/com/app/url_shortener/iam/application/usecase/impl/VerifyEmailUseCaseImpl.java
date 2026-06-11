package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.VerifyEmailCommand;
import com.app.url_shortener.iam.application.port.output.*;
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

  private static final String SUCCESS_MESSAGE = "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";

  private final RoleRepositoryPort roleRepositoryPort;
  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenStorePort  emailVerificationTokenStorePort;

  @Override
  @Transactional
  public VerifyEmailResult execute(VerifyEmailCommand command) {
    checkAuthRateLimitPort.checkVerifyEmail(command.email());

    var userAccount = fetchUserAccountByEmail(command.email());
    var emailVerificationToken = consumeEmailVerificationToken(userAccount.getEmail(), command.code());
    verifyAccountEmail(userAccount, emailVerificationToken);

    return new VerifyEmailResult(SUCCESS_MESSAGE);
  }

  private UserAccount fetchUserAccountByEmail(String email) {
    return userAccountRepositoryPort
            .findByEmailWithRoles(email)
            .orElseThrow(InvalidOrExpiredEmailVerificationCodeException::new);
  }

  private void verifyAccountEmail(UserAccount userAccount, EmailVerificationToken emailVerificationToken) {
    if (!emailVerificationToken.userId().equals(userAccount.getId())) {
      throw new InvalidOrExpiredEmailVerificationCodeException();
    }

    Role defaultRole = roleRepositoryPort.findDefaultRole();
    userAccount.verifyEmail(defaultRole);
    userAccountRepositoryPort.save(userAccount);
  }

  private EmailVerificationToken consumeEmailVerificationToken(String userEmail, VerificationCode verificationCode) {
    EmailVerificationToken token = emailVerificationTokenStorePort
            .consumeByEmailAndCode(userEmail, verificationCode)
            .orElseThrow(InvalidOrExpiredEmailVerificationCodeException::new);

    if (token.isExpired()) {
      throw new InvalidOrExpiredEmailVerificationCodeException();
    }

    return token;
  }
}
