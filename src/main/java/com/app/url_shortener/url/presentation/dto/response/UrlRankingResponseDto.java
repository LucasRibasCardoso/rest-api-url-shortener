package com.app.url_shortener.url.presentation.dto.response;

import java.util.List;

public record UrlRankingResponseDto(List<UrlRankingItemResponseDto> urls) {}
