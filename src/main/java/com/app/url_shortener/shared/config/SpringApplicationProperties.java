package com.app.url_shortener.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "spring.application")
public record SpringApplicationProperties(@NotBlank String name) {}
