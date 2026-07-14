package com.app.url_shortener.iam.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para registro de uma nova conta de usuário.")
public record RegisterRequestDto(
    @Schema(description = "Nome do usuário.", example = "Maria da Silva")
        @NotBlank
        @Size(min = 3, max = 120)
        String name,
    @Schema(description = "E-mail único do usuário.", example = "maria@example.com")
        @NotBlank
        @Email
        @Size(max = 180)
        String email,
    @Schema(
            description = "Senha da conta.",
            example = "<password>",
            accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank
        @Size(min = 6, max = 128)
        String password) {

  @Override
  public String toString() {
    return "RegisterRequestDto{"
        + "name='"
        + name
        + '\''
        + ", email='"
        + email
        + '\''
        + ", password='[REDACTED]'"
        + '}';
  }
}
