package com.app.url_shortener.url.application.result;

import java.time.Instant;

public record ShortenUrlResult(String originalUrl, String shortCode, Instant createdAt) {}
