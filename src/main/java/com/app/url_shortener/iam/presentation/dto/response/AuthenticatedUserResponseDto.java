package com.app.url_shortener.iam.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import java.util.UUID;

@Schema(description = "Dados públicos do usuário autenticado.")
public record AuthenticatedUserResponseDto(
    @Schema(
            description = "Identificador público do usuário.",
            example = "019a16f1-ae7f-7c9d-9e18-44773f1ac123")
        UUID id,
    @Schema(description = "Nome do usuário.", example = "User Name") String name,
    @Schema(description = "E-mail do usuário.", example = "user@example.com") String email,
    @Schema(description = "Plano atual do usuário.", example = "FREE") String plan,
    @ArraySchema(
            schema =
                @Schema(description = "Role do usuário sem o prefixo ROLE_.", example = "USER"))
        List<String> roles) {}
