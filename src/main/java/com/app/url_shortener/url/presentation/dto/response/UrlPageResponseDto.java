package com.app.url_shortener.url.presentation.dto.response;

import java.util.List;

public record UrlPageResponseDto(List<UrlResponseDto> urls, String nextCursor) {
}
