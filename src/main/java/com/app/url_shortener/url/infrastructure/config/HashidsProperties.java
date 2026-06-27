package com.app.url_shortener.url.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.hashids")
public record HashidsProperties(@NotBlank String salt, @Positive int minLength) {

  @Override
  public String toString() {
    return "HashidsProperties{salt='[REDACTED]', minLength=" + minLength + '}';
  }
}
