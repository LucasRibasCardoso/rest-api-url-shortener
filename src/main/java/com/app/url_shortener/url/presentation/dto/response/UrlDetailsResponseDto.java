package com.app.url_shortener.url.presentation.dto.response;

import com.app.url_shortener.url.domain.model.UrlStatus;
import java.time.Instant;
import java.util.UUID;

public record UrlDetailsResponseDto(
    String shortCode,
    String originalUrl,
    UUID userId,
    UrlStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    UUID deletedBy) {
}
