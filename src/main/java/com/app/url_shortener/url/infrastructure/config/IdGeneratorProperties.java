package com.app.url_shortener.url.infrastructure.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.id-generator")
public record IdGeneratorProperties(@Positive long blockSize, @NotBlank String counterName) {}
