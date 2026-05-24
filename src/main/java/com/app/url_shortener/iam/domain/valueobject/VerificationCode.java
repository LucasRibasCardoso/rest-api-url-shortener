package com.app.url_shortener.iam.domain.valueobject;

import com.app.url_shortener.iam.domain.exception.user.InvalidVerificationCodeException;
import java.security.SecureRandom;

public record VerificationCode(String value) {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  public VerificationCode {
    if (value == null || !value.matches("\\d{6}")) {
      throw new InvalidVerificationCodeException();
    }
  }

  public static VerificationCode of(String value) {
    return new VerificationCode(value);
  }

  public static VerificationCode generate() {
    return new VerificationCode(String.format("%06d", SECURE_RANDOM.nextInt(1_000_000)));
  }

  @Override
  public String toString() {
    return "VerificationCode{value='[REDACTED]'}";
  }
}
