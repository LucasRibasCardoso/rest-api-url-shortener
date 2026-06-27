package com.app.url_shortener.iam.application.command;

public record RefreshTokenCommand(String refreshToken) {

  @Override
  public String toString() {
    return "RefreshTokenCommand{refreshToken='[REDACTED]'}";
  }
}
