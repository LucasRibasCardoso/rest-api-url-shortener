package com.app.url_shortener.shared.outbox.config;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.outbox.publisher")
public record OutboxPublisherProperties(
    boolean enabled,
    @Positive int batchSize,
    @NotNull @DurationMin(nanos = 1) Duration fixedDelay,
    @NotNull @DurationMin(nanos = 1) Duration initialDelay,
    @Positive int maxAttempts,
    @NotNull @DurationMin(nanos = 1) Duration retryDelay) {}
