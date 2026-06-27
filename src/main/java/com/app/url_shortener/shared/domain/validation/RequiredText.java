package com.app.url_shortener.shared.domain.validation;

import java.util.Objects;

public final class RequiredText {

  private RequiredText() {}

  public static String normalize(String value, String fieldName) {
    String normalizedValue = Objects.requireNonNull(value, fieldName + " is required").trim();

    if (normalizedValue.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }

    return normalizedValue;
  }
}
