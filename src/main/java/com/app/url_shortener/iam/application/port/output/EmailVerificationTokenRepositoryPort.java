package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface EmailVerificationTokenRepositoryPort {

  EmailVerificationToken save(EmailVerificationToken token);

  Optional<EmailVerificationToken> findById(UUID tokenId);

  Optional<EmailVerificationToken> findOpenByUserIdAndEmail(UUID userId, String email);

  Optional<EmailVerificationToken> findActiveByUserIdAndEmail(UUID userId, String email, Instant now);

  int revokeOpenByUserIdAndEmail(UUID userId, String email, Instant now);

  boolean consumeIfActive(UUID tokenId, Instant now);

  boolean registerFailedAttempt(UUID tokenId, Instant now);
}
