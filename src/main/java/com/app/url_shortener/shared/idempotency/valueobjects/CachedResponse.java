package com.app.url_shortener.shared.idempotency.valueobjects;

public record CachedResponse(int status, String body) {}
