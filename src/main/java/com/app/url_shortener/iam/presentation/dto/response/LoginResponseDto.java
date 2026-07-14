package com.app.url_shortener.iam.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resposta de autenticação com access token e dados básicos do usuário.")
public record LoginResponseDto(
    @Schema(
            description = "JWT access token usado no header Authorization.",
            example = "<access_token>",
            accessMode = Schema.AccessMode.READ_ONLY)
        String accessToken,
    @Schema(description = "Tipo do token emitido.", example = "Bearer") String tokenType,
    @Schema(description = "Tempo de expiração do access token em segundos.", example = "900")
        Long expiresInSeconds,
    @Schema(description = "Usuário autenticado.") AuthenticatedUserResponseDto user) {

  @Override
  public String toString() {
    return "LoginResponseDto{"
        + "accessToken='[REDACTED]'"
        + ", tokenType='"
        + tokenType
        + '\''
        + ", expiresInSeconds="
        + expiresInSeconds
        + ", user="
        + user
        + '}';
  }
}
