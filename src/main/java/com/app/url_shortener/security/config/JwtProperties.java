package com.app.url_shortener.security.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.jwt")
public record JwtProperties(
        String issuer,
        String secret,
        long accessTokenExpirationSeconds
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
