package com.app.url_shortener.iam.domain.event;

import com.app.url_shortener.iam.domain.valueobject.VerificationCode;

import java.util.UUID;

public record EmailVerificationEvent(UUID userId, String email, VerificationCode code) {

  @Override
  public String toString() {
    return "EmailVerificationEvent{"
        + "userId="
        + userId
        + ", email='"
        + email
        + '\''
        + ", code='[REDACTED]'"
        + '}';
  }
}
