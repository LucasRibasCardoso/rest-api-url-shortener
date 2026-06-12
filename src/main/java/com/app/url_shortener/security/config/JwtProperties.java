package com.app.url_shortener.security.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        @NotBlank String issuer,
        @NotBlank String secret,
        @Positive long accessTokenExpirationSeconds
) {

  @Override
  public String toString() {
    return "JwtProperties{"
        + "issuer='"
        + issuer
        + '\''
        + ", secret='[REDACTED]'"
        + ", accessTokenExpirationSeconds="
        + accessTokenExpirationSeconds
        + '}';
  }
}
