package com.app.url_shortener.url.presentation.dto.request;

import com.app.url_shortener.url.presentation.validator.ValidHttpUrl;
import jakarta.validation.constraints.NotBlank;

public record ShortenUrlRequestDto(@NotBlank @ValidHttpUrl String originalUrl) {}
