package com.app.url_shortener.url.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.url.redirect-cache")
public record RedirectCacheProperties(@Valid @NotNull Ttl ttl) {

  public record Ttl(
      @NotNull @DurationMin(nanos = 1) Duration active,
      @NotNull @DurationMin(nanos = 1) Duration deleted,
      @NotNull @DurationMin(nanos = 1) Duration notFound) {}
}
