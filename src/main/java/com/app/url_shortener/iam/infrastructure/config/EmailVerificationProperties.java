package com.app.url_shortener.iam.infrastructure.config;

import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.iam.email-verification")
public record EmailVerificationProperties(
    @NotNull @DurationMin(nanos = 1) Duration codeTtl,
    @NotNull @DurationMin(nanos = 1) Duration idempotencyTtl) {}
