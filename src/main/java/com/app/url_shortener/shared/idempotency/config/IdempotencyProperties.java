package com.app.url_shortener.shared.idempotency.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(@NotEmpty List<@NotBlank String> protectedUris) {}
