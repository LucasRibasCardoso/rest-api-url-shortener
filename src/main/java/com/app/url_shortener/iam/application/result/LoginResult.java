package com.app.url_shortener.iam.application.result;

public record LoginResult(
        String refreshToken,
        String accessToken,
        String tokenType,
        Long expiresInSeconds,
        AuthenticatedUserResult user
) {

  @Override
  public String toString() {
    return "LoginResult{"
        + "refreshToken='[REDACTED]'"
        + ", accessToken='[REDACTED]'"
        + ", tokenType='"
        + tokenType
        + '\''
        + ", expiresInSeconds="
        + expiresInSeconds
        + ", user="
        + user
        + '}';
  }
}
