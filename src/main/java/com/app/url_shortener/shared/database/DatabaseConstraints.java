package com.app.url_shortener.shared.database;

import java.util.Arrays;
import java.util.Optional;

public enum DatabaseConstraints {

  UK_USERS_EMAIL("uk_users_email"),
  UK_EMAIL_DISPATCHES_EVENT_ID("uk_email_dispatches_event_id"),
  UK_EMAIL_VERIFICATION_TOKENS_OPEN_USER_EMAIL("uk_email_verification_tokens_open_user_email"),;

  private final String value;

  DatabaseConstraints(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }

  public static Optional<DatabaseConstraints> fromValue(String value) {
    return Arrays.stream(values())
            .filter(constraint -> constraint.value.equals(value))
            .findFirst();
  }

}
