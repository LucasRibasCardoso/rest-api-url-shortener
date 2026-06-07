package com.app.url_shortener.url.presentation.dto.response;

import com.app.url_shortener.url.domain.model.UrlStatus;

import java.time.Instant;

public record UrlResponseDto(
    String originalUrl,
    String shortCode,
    String shortUrl,
    Instant createdAt,
    UrlStatus status) {}
