package com.app.url_shortener.iam.application.event;

import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

public record EmailVerificationRequestedPayload(
    UUID userId,
    String email,
    EmailDispatchReason reason) {

  public EmailVerificationRequestedPayload {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(reason, "reason must not be null");

    email = RequiredText.normalize(email, "email").toLowerCase(Locale.ROOT);
  }
}
