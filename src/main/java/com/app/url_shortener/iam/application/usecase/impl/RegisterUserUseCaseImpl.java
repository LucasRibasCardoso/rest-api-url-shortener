package com.app.url_shortener.iam.application.usecase.impl;

import com.app.url_shortener.iam.application.command.RegisterUserCommand;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.port.output.EmailVerificationEventPublisherPort;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.application.port.output.PasswordEncoderPort;
import com.app.url_shortener.iam.application.port.output.UserAccountRepositoryPort;
import com.app.url_shortener.iam.application.result.RegisterUserResult;
import com.app.url_shortener.iam.application.usecase.RegisterUserUseCase;
import com.app.url_shortener.iam.domain.model.UserAccount;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RegisterUserUseCaseImpl implements RegisterUserUseCase {

  private static final String SUCCESS_MESSAGE =
      "Conta criada com sucesso. Enviamos um código de verificação para o seu e-mail.";
  private static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(10);

  private final PasswordEncoderPort passwordEncoderPort;
  private final UserAccountRepositoryPort userAccountRepositoryPort;
  private final EmailVerificationTokenStorePort emailVerificationTokenStorePort;
  private final EmailVerificationEventPublisherPort emailVerificationEventPublisherPort;

  @Override
  @Transactional
  public RegisterUserResult execute(RegisterUserCommand command) {
    String passwordHash = passwordEncoderPort.encode(command.password());

    UserAccount userAccount = UserAccount.createPendingRegistration(command.name(), command.email(), passwordHash);
    UserAccount savedUserAccount = userAccountRepositoryPort.create(userAccount);

    VerificationCode verificationCode = VerificationCode.generate();
    EmailVerificationToken emailVerificationToken =
        EmailVerificationToken.create(
            savedUserAccount.getId(),
            savedUserAccount.getEmail(),
            verificationCode,
            Instant.now().plus(VERIFICATION_CODE_TTL));

    emailVerificationTokenStorePort.store(emailVerificationToken, VERIFICATION_CODE_TTL);
    emailVerificationEventPublisherPort.publish(
        EmailVerificationRequestedEvent.create(
            savedUserAccount.getId(), savedUserAccount.getEmail(), verificationCode));

    return new RegisterUserResult(SUCCESS_MESSAGE);
  }
}
