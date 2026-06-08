package com.app.url_shortener.iam.application.port.output.model;

public record IssuedAccessToken(String value, long expiresInSeconds) {

  @Override
  public String toString() {
    return "IssuedAccessToken{value='[REDACTED]', expiresInSeconds='[REDACTED]'}";
  }
}
