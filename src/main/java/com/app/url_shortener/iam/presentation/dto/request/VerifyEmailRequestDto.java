package com.app.url_shortener.iam.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para confirmar o e-mail de uma conta pendente.")
public record VerifyEmailRequestDto(
    @Schema(description = "E-mail informado no registro.", example = "user@example.com")
        @NotBlank
        @Email
        @Size(max = 180)
        String email,
    @Schema(
            description = "Código de verificação de 6 dígitos enviado ao e-mail do usuário.",
            example = "123456",
            accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Pattern(regexp = "\\d{6}", message = "Código de verificação deve conter 6 digitos")
        String code) {

  @Override
  public String toString() {
    return "VerifyEmailRequestDto{email='" + email + "', code='[REDACTED]'}";
  }
}
