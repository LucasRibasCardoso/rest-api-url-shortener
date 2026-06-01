package com.app.url_shortener.url.application.result;

import java.time.Instant;

public record UrlDetailsResult(String originalUrl, String shortCode, Instant createdAt) {
}
