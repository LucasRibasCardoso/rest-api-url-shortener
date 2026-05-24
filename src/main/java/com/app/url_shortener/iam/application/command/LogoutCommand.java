package com.app.url_shortener.iam.application.command;

public record LogoutCommand(String refreshToken) {

  @Override
  public String toString() {
    return "LogoutCommand{refreshToken='[REDACTED]'}";
  }
}
