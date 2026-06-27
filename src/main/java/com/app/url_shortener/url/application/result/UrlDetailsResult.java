package com.app.url_shortener.url.application.result;

import com.app.url_shortener.url.domain.model.UrlStatus;
import java.time.Instant;
import java.util.UUID;

public record UrlDetailsResult(
    String shortCode,
    String originalUrl,
    UUID userId,
    UrlStatus status,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt,
    UUID deletedBy,
    long accessCount,
    Instant lastAccessedAt) {}
