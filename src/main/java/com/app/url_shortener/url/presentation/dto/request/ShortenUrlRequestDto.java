package com.app.url_shortener.url.presentation.dto.request;

import com.app.url_shortener.url.presentation.validator.ValidHttpUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Dados para criação de uma URL encurtada.")
public record ShortenUrlRequestDto(
    @Schema(
            description = "URL original que será encurtada. Deve usar HTTP ou HTTPS.",
            example = "https://example.com/articles/spring-boot")
        @NotBlank
        @ValidHttpUrl
        String originalUrl) {}
