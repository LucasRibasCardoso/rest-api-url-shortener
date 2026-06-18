package com.app.url_shortener.iam.infrastructure.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "app.iam.email-verification")
@Validated
public record EmailVerificationProperties(
    @NotNull @DurationMin(nanos = 1) Duration codeTtl,
    @NotNull @DurationMin(nanos = 1) Duration sendingTimeout,
    @NotNull @DurationMin(nanos = 1) Duration resendCooldown,
    @Valid @NotNull CodeProtection codeProtection) {

  public record CodeProtection(
      @NotBlank @Size(min = 32) String encryptionPassword,
      @NotBlank @Pattern(regexp = "^[0-9a-fA-F]{16}$") String encryptionSalt,
      @NotBlank String hmacSecret) {}
}
