package com.app.url_shortener.url.presentation.dto.response;

import com.app.url_shortener.url.domain.model.UrlStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Detalhes completos de uma URL encurtada.")
public record UrlDetailsResponseDto(
    @Schema(description = "Código curto da URL encurtada.", example = "aB3dE") String shortCode,
    @Schema(
            description = "URL original associada ao short code.",
            example = "https://example.com/articles/spring-boot")
        String originalUrl,
    @Schema(
            description = "Identificador do usuário dono da URL.",
            example = "019a16f1-ae7f-7c9d-9e18-44773f1ac001")
        UUID userId,
    @Schema(description = "Status atual da URL encurtada.", example = "ACTIVE") UrlStatus status,
    @Schema(description = "Data e hora de criação da URL.", example = "2026-05-10T14:30:00Z")
        Instant createdAt,
    @Schema(
            description = "Data e hora da última atualização da URL.",
            example = "2026-05-10T14:30:00Z")
        Instant updatedAt,
    @Schema(
            description = "Data e hora de exclusão, presente apenas para URLs deletadas.",
            example = "2026-05-11T10:00:00Z")
        Instant deletedAt,
    @Schema(
            description = "Usuário que removeu a URL, presente apenas para URLs deletadas.",
            example = "019a16f1-ae7f-7c9d-9e18-44773f1ac001")
        UUID deletedBy,
    @Schema(description = "Quantidade de acessos registrados para a URL.", example = "42")
        long accessCount,
    @Schema(
            description = "Data e hora do último acesso registrado, quando houver.",
            example = "2026-05-11T10:00:00Z")
        Instant lastAccessedAt) {}
