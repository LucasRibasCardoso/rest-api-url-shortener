package com.app.url_shortener.shared.infrastructure.idempotency.valueobjects;

public record CachedResponse(int status, String body) {}
