package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Duration;
import java.util.Optional;

public interface EmailVerificationTokenPort {

  void store(EmailVerificationToken token, Duration ttl);

  Optional<EmailVerificationToken> consumeByEmailAndCode(String email, VerificationCode code);
}
