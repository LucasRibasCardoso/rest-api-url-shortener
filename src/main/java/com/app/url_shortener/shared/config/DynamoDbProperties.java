package com.app.url_shortener.shared.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "aws.dynamodb")
public record DynamoDbProperties(
    @NotBlank String endpoint,
    @NotBlank String region,
    @NotBlank String accessKey,
    @NotBlank String secretKey,
    @Valid @NotNull Tables tables) {

  public record Tables(@NotBlank String url, @NotBlank String urlCounter) {}

  @Override
  public String toString() {
    return "DynamoDbProperties{"
        + "endpoint='" + endpoint + '\''
        + ", region='" + region + '\''
        + ", accessKey='[REDACTED]'"
        + ", secretKey='[REDACTED]'"
        + ", tables=" + tables
        + '}';
  }
}
