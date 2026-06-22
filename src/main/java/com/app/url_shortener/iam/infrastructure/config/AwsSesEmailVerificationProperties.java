package com.app.url_shortener.iam.infrastructure.config;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.iam.email-verification.ses")
@Validated
public record AwsSesEmailVerificationProperties(
    String endpoint,
    @NotBlank @Email String fromEmail,
    @NotBlank String subject,
    @NotNull @DurationMin(nanos = 1) Duration apiCallTimeout) {}
