package com.app.url_shortener.iam.presentation.dto.response;

public record LoginResponseDto(
    String accessToken,
    String tokenType,
    Long expiresInSeconds,
    AuthenticatedUserResponseDto user) {

  @Override
  public String toString() {
    return "LoginResponseDto{"
        + "accessToken='[REDACTED]'"
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
