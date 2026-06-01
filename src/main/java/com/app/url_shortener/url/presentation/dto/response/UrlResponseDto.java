package com.app.url_shortener.url.presentation.dto.response;

import java.time.Instant;

public record UrlResponseDto(String originalUrl, String shortUrl, Instant createdAt) {
}
