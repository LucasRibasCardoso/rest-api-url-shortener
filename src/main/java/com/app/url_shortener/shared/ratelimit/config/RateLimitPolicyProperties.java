package com.app.url_shortener.shared.ratelimit.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;

public record RateLimitPolicyProperties(
    boolean enabled,
    @Min(1) long capacity,
    @Min(1) long refillTokens,
    @NotNull @DurationMin(nanos = 1) Duration refillPeriod,
    boolean failOpen) {}
