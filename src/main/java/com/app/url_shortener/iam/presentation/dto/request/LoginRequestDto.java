package com.app.url_shortener.iam.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Credenciais de login do usuário.")
public record LoginRequestDto(
    @Schema(description = "E-mail do usuário.", example = "user@example.com")
        @NotBlank
        @Email
        @Size(max = 180)
        String email,
    @Schema(
            description = "Senha do usuário.",
            example = "StrongPassword123!",
            accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Size(min = 6, max = 128)
        String password) {

  @Override
  public String toString() {
    return "LoginRequestDto{email='" + email + "', password='[REDACTED]'}";
  }
}
