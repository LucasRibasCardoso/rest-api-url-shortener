package com.app.url_shortener.shared.config.properties;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;

public record RateLimitPolicyProperties(
    boolean enabled,
    @Min(1) long capacity,
    @Min(1) long refillTokens,
    @NotNull Duration refillPeriod,
    boolean failOpen) {}
