package com.app.url_shortener.iam.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta de renovação de sessão com novo access token.")
public record RefreshTokenResponseDto(
    @Schema(
            description = "Novo JWT access token usado no header Authorization.",
            example = "<access_token>",
            accessMode = Schema.AccessMode.READ_ONLY)
        String newAccessToken) {

  @Override
  public String toString() {
    return "RefreshTokenResponseDto{newAccessToken='[REDACTED]'}";
  }
}
