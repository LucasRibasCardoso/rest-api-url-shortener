package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.VerifyEmailCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenPort;
import com.app.url_shortener.iam.application.port.output.RoleRepositoryPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.VerifyEmailResult;
import com.app.url_shortener.iam.application.usecase.VerifyEmailUseCase;
import com.app.url_shortener.iam.domain.exception.auth.EmailVerificationTokenExpiredException;
import com.app.url_shortener.iam.domain.exception.user.InvalidVerificationCodeException;
import com.app.url_shortener.iam.domain.exception.user.UserNotFoundException;
import com.app.url_shortener.iam.domain.model.Role;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class VerifyEmailUseCaseImpl implements VerifyEmailUseCase {

  private static final String SUCCESS_MESSAGE = "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";

  private final RoleRepositoryPort roleRepositoryPort;
  private final CheckAuthRateLimitPort  checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenPort emailVerificationTokenPort;

  @Override
  @Transactional
  public VerifyEmailResult execute(VerifyEmailCommand command) {
    String email = command.email();
    VerificationCode code = command.code();

    checkAuthRateLimitPort.checkVerifyEmail(email);

    EmailVerificationToken token = validateEmailVerificationToken(emailVerificationTokenPort.findByEmail(email));
    if (!token.matches(code)) {
      throw new InvalidVerificationCodeException();
    }

    UserAccount userAccount = userAccountRepositoryPort.findByEmailWithRoles(email)
            .orElseThrow(UserNotFoundException::new);

    Role defaultRole = roleRepositoryPort.findDefaultRole();
    userAccount.verifyEmail(defaultRole);
    userAccountRepositoryPort.save(userAccount);

    registerTokenDeletionAfterCommit(email);
    return new VerifyEmailResult(SUCCESS_MESSAGE);
  }

  private void registerTokenDeletionAfterCommit(String email) {
    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
      @Override
      public void afterCommit() {
        emailVerificationTokenPort.deleteByEmail(email);
      }
    });
  }

  private EmailVerificationToken validateEmailVerificationToken(Optional<EmailVerificationToken> token) {
    if (token.isEmpty() || token.get().isExpired()) {
      throw new EmailVerificationTokenExpiredException();
    }

    return token.get();
  }
}
