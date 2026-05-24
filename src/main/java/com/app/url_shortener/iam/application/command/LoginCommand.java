package com.app.url_shortener.iam.application.command;

import java.util.Locale;

public record LoginCommand(String email, String password) {
  public LoginCommand {
    email = email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
  }

  @Override
  public String toString() {
    return "LoginCommand{email='" + email + "', password='[REDACTED]'}";
  }
}
