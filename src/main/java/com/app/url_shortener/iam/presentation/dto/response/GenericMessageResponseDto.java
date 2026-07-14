package com.app.url_shortener.iam.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta simples com mensagem de resultado da operação.")
public record GenericMessageResponseDto(
    @Schema(
            description = "Mensagem de resultado.",
            example = "Enviamos um código de verificação para o seu e-mail.")
        String message) {}
