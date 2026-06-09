package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.RegisterUserCommand;
import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventPublisherPort;
import com.app.url_shortener.iam.application.port.output.PasswordEncoderPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.RegisterUserResult;
import com.app.url_shortener.iam.application.usecase.RegisterUserUseCase;
import com.app.url_shortener.iam.domain.model.UserAccount;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

  private static final String SUCCESS_MESSAGE = "Enviamos um código de verificação para o seu e-mail.";

  private final PasswordEncoderPort passwordEncoderPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationEventPublisherPort emailVerificationEventPublisherPort;

  @Override
  @Transactional
  public RegisterUserResult execute(RegisterUserCommand command) {
    String passwordHash = passwordEncoderPort.encode(command.password());

    UserAccount userAccount = UserAccount.createPendingRegistration(command.name(), command.email(), passwordHash);
    UserAccount savedUserAccount = userAccountRepositoryPort.create(userAccount);

    emailVerificationEventPublisherPort.publish(EmailVerificationRequestedEvent.create(
            savedUserAccount.getId(),
            savedUserAccount.getEmail(),
            EmailVerificationReason.REGISTER)
    );

    return new RegisterUserResult(SUCCESS_MESSAGE);
  }
}
