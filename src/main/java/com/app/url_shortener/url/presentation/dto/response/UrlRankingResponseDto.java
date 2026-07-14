package com.app.url_shortener.url.presentation.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "Ranking das URLs mais acessadas do usuário autenticado.")
public record UrlRankingResponseDto(
    @Schema(description = "URLs ordenadas por quantidade de acessos em ordem decrescente.")
        List<UrlRankingItemResponseDto> urls) {}
