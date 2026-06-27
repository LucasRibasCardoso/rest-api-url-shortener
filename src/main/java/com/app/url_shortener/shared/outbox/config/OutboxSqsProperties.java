package com.app.url_shortener.shared.outbox.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.outbox.sqs")
public record OutboxSqsProperties(@NotEmpty Map<@NotBlank String, @NotBlank String> queues) {}
