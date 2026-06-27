package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.RegisterUserCommand;
import com.app.url_shortener.iam.application.port.output.CheckAuthRateLimitPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationOutboxPort;
import com.app.url_shortener.iam.application.port.output.PasswordEncoderPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.RegisterUserResult;
import com.app.url_shortener.iam.application.usecase.RegisterUserUseCase;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.model.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

  private static final String SUCCESS_MESSAGE =
      "Enviamos um código de verificação para o seu e-mail.";

  private final PasswordEncoderPort passwordEncoderPort;
  private final CheckAuthRateLimitPort checkAuthRateLimitPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationOutboxPort emailVerificationEventPort;

  @Override
  @Transactional
  public RegisterUserResult execute(RegisterUserCommand command) {
    checkAuthRateLimitPort.checkRegister(command.clientIp(), command.email());

    String passwordHash = passwordEncoderPort.encode(command.password());

    var userAccount =
        UserAccount.createPendingRegistration(command.name(), command.email(), passwordHash);

    var savedUserAccount = userAccountRepositoryPort.create(userAccount);

    emailVerificationEventPort.publishEmailVerificationRequestedEvent(
        savedUserAccount.getId(), savedUserAccount.getEmail(), EmailDispatchReason.REGISTER);

    return new RegisterUserResult(SUCCESS_MESSAGE);
  }
}
