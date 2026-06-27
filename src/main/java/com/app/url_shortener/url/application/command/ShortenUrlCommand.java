package com.app.url_shortener.url.application.command;

import com.app.url_shortener.iam.domain.enums.PlanType;
import java.util.UUID;

public record ShortenUrlCommand(UUID userId, String originalUrl, PlanType planType) {

  public ShortenUrlCommand {
    originalUrl = originalUrl != null ? originalUrl.trim() : null;
  }
}
