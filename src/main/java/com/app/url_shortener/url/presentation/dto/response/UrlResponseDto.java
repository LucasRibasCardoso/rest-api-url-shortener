package com.app.url_shortener.url.presentation.dto.response;

import com.app.url_shortener.url.domain.model.UrlStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

@Schema(description = "URL encurtada criada.")
public record UrlResponseDto(
    @Schema(
            description = "URL original informada pelo usuário.",
            example = "https://example.com/articles/spring-boot")
        String originalUrl,
    @Schema(description = "Código curto gerado para a URL.", example = "aB3dE") String shortCode,
    @Schema(description = "URL curta completa.", example = "http://localhost:8080/r/aB3dE")
        String shortUrl,
    @Schema(
            description = "Data e hora de criação da URL encurtada.",
            example = "2026-05-10T14:30:00Z")
        Instant createdAt,
    @Schema(description = "Status atual da URL encurtada.", example = "ACTIVE") UrlStatus status) {}
