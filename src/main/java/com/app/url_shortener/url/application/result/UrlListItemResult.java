package com.app.url_shortener.url.application.result;

import com.app.url_shortener.url.domain.model.UrlStatus;

import java.time.Instant;

public record UrlListItemResult(
    String originalUrl,
    String shortCode,
    Instant createdAt,
    UrlStatus status) {}
