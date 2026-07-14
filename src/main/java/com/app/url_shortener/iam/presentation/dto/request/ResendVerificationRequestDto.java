package com.app.url_shortener.iam.presentation.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Dados para solicitar o reenvio do código de verificação de e-mail.")
public record ResendVerificationRequestDto(
    @Schema(description = "E-mail informado no registro.", example = "user@example.com")
        @NotBlank
        @Email
        @Size(max = 180)
        String email) {}
