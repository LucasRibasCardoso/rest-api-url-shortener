package com.app.url_shortener.shared.idempotency.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "app.idempotency")
public record IdempotencyProperties(List<String> protectedUris) {
}
