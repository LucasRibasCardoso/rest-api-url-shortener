package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.VerifyEmailCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenPort;
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

  private static final String SUCCESS_MESSAGE = "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";

  private final RoleRepositoryPort roleRepositoryPort;
  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenPort emailVerificationTokenPort;

  @Override
  @Transactional
  public VerifyEmailResult execute(VerifyEmailCommand command) {

    checkAuthRateLimitPort.checkVerifyEmail(command.email());
    EmailVerificationToken token = consumeAndValidateToken(command.code(), command.email());
    verifyAccountEmail(command.email(), token);

    return new VerifyEmailResult(SUCCESS_MESSAGE);
  }

  private EmailVerificationToken consumeAndValidateToken(VerificationCode code, String email) {
    EmailVerificationToken token =
        emailVerificationTokenPort
            .consumeByEmailAndCode(email, code)
            .orElseThrow(InvalidOrExpiredEmailVerificationCodeException::new);

    if (token.isExpired()) {
      throw new InvalidOrExpiredEmailVerificationCodeException();
    }

    return token;
  }

  private void verifyAccountEmail(String email, EmailVerificationToken token) {
    UserAccount user =
        userAccountRepositoryPort
            .findByEmailWithRoles(email)
            .orElseThrow(InvalidOrExpiredEmailVerificationCodeException::new);

    if (!token.userId().equals(user.getId())) {
      throw new InvalidOrExpiredEmailVerificationCodeException();
    }

    Role defaultRole = roleRepositoryPort.findDefaultRole();
    user.verifyEmail(defaultRole);
    userAccountRepositoryPort.save(user);
  }
}
