package com.app.url_shortener.iam.application.command;

import java.util.Locale;
import java.util.regex.Pattern;

public record RegisterUserCommand(
    String name,
    String email,
    String password) {

  private static final Pattern MULTIPLE_SPACES = Pattern.compile("\\s+");

  public RegisterUserCommand {
    email = email != null ? email.trim().toLowerCase(Locale.ROOT) : null;
    name = name != null ? MULTIPLE_SPACES.matcher(name.trim()).replaceAll(" ") : null;
  }

  @Override
  public String toString() {
    return "RegisterUserCommand{"
        + "name='"
        + name
        + '\''
        + ", email='"
        + email
        + '\''
        + ", password='[REDACTED]'"
        + '}';
  }
}
