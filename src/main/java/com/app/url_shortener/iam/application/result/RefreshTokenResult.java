package com.app.url_shortener.iam.application.result;

public record RefreshTokenResult(String newRefreshToken, String newAccessToken) {

  @Override
  public String toString() {
    return "RefreshTokenResult{newRefreshToken='[REDACTED]', newAccessToken='[REDACTED]'}";
  }
}
