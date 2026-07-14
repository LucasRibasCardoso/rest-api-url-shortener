package com.app.url_shortener.url.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Página de URLs encurtadas.")
public record UrlPageResponseDto(
    @Schema(description = "URLs retornadas na página atual.") List<UrlResponseDto> urls,
    @Schema(
            description = "Cursor para buscar a próxima página. Ausente ou nulo na última página.",
            example = "eyJzaG9ydENvZGUiOiJhQjNkRSJ9")
        String nextCursor) {}
