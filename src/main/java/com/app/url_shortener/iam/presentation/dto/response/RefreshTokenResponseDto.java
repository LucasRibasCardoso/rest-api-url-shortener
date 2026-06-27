package com.app.url_shortener.iam.presentation.dto.response;

public record RefreshTokenResponseDto(String newAccessToken) {

  @Override
  public String toString() {
    return "RefreshTokenResponseDto{newAccessToken='[REDACTED]'}";
  }
}
