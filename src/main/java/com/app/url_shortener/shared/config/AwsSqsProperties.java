package com.app.url_shortener.shared.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "app.aws.sqs")
public record AwsSqsProperties(
    @NotBlank String urlRedirectEventsQueue,
    @NotBlank String urlRedirectEventsDlq,
    @NotBlank String emailVerificationEventsQueue,
    @NotBlank String emailVerificationEventsDlq) {}
