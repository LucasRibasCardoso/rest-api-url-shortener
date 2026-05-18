package com.app.url_shortener.url.domain.model;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class Url implements Serializable {
  private final UUID userId;
  private final String shortCode;
  private final String originalUrl;
  private final LocalDateTime createdAt;

  private Url(UUID userId, String shortCode, String originalUrl, LocalDateTime createdAt) {
    this.userId = Objects.requireNonNull(userId, "userId is required.");
    this.shortCode = Objects.requireNonNull(shortCode, "shortCode is required.").trim();
    this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl is required.").trim();
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt is  required");
  }

  public static Url create(UUID userId, String shortCode, String originalUrl) {
    return new Url(userId, shortCode, originalUrl, LocalDateTime.now());
  }

  public static Url restore(
      UUID userId, String shortCode, String originalUrl, LocalDateTime createdAt) {
    return new Url(userId, shortCode, originalUrl, createdAt);
  }

  @Override
  public String toString() {
    return "Url{"
        + "userId="
        + userId
        + ", shortCode='"
        + shortCode
        + '\''
        + ", originalUrl='"
        + originalUrl
        + '\''
        + ", createdAt="
        + createdAt
        + '}';
  }
}
