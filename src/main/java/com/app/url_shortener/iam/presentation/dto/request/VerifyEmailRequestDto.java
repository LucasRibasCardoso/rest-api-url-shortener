package com.app.url_shortener.iam.presentation.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record VerifyEmailRequestDto(
    @NotBlank @Email @Size(max = 180) String email,
    @NotBlank @Pattern(regexp = "\\d{6}", message = "Código de verificação deve conter 6 digitos")
        String code) {

  @Override
  public String toString() {
    return "VerifyEmailRequestDto{email='" + email + "', code='[REDACTED]'}";
  }
}
