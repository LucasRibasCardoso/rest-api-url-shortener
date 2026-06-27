package com.app.url_shortener.url.presentation.dto.response;

import com.app.url_shortener.url.domain.model.UrlStatus;
import java.time.Instant;

public record UrlRankingItemResponseDto(
    String originalUrl,
    String shortUrl,
    Instant createdAt,
    UrlStatus status,
    long accessCount,
    Instant lastAccessedAt) {}
