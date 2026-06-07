package com.app.url_shortener.url.domain.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Getter
public class Url implements Serializable {
  private final UUID userId;
  private final String shortCode;
  private final String originalUrl;
  private final Instant createdAt;
  private final UrlStatus status;
  private final Instant deletedAt;
  private final UUID deletedBy;
  private final Instant updatedAt;
  private final long accessCount;
  private final Instant lastAccessedAt;

  private Url(
      UUID userId,
      String shortCode,
      String originalUrl,
      Instant createdAt,
      UrlStatus urlStatus,
      Instant deletedAt,
      UUID deletedBy,
      Instant updatedAt,
      long accessCount,
      Instant lastAccessedAt) {
    this.userId = Objects.requireNonNull(userId, "userId is required.");
    this.shortCode = validateNotBlank(shortCode, "shortCode");
    this.originalUrl = validateNotBlank(originalUrl, "originalUrl");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required.");
    this.status = Objects.requireNonNull(urlStatus, "urlStatus is required.");
    this.deletedAt = deletedAt;
    this.deletedBy = deletedBy;
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required.");
    this.accessCount = accessCount;
    this.lastAccessedAt = lastAccessedAt;

    validateStatusConsistency();
    validateAccessCount(accessCount);
  }

  public static Url create(UUID userId, String shortCode, String originalUrl) {
    Instant now = Instant.now();
    return new Url(userId, shortCode, originalUrl, now, UrlStatus.ACTIVE, null, null, now, 0, null);
  }

  public static Url restore(
      UUID userId,
      String shortCode,
      String originalUrl,
      Instant createdAt,
      UrlStatus urlStatus,
      Instant deletedAt,
      UUID deletedBy,
      Instant updatedAt,
      long accessCount,
      Instant lastAccessedAt) {
    return new Url(
        userId,
        shortCode,
        originalUrl,
        createdAt,
        urlStatus,
        deletedAt,
        deletedBy,
        updatedAt,
        accessCount,
        lastAccessedAt);
  }

  private static void validateAccessCount(long accessCount) {
    if (accessCount < 0) {
      throw new IllegalArgumentException("accessCount must not be negative.");
    }
  }

  private static String validateNotBlank(String value, String field) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(field + " is required.");
    }

    return value.trim();
  }

  public boolean isDeleted() {
    return status == UrlStatus.DELETED && deletedAt != null && deletedBy != null;
  }

  public boolean isActive() {
    return status == UrlStatus.ACTIVE && deletedAt == null && deletedBy == null;
  }

  public boolean isRedirectable() {
    return isActive();
  }

  /**
   * Garante que o status da URL seja consistente com seus metadados de exclusão.
   *
   * <p>URLs ativas não podem possuir {@code deletedAt} ou {@code deletedBy}, e URLs excluídas
   * precisam conter ambos os metadados.
   *
   * @throws IllegalArgumentException se a combinação de status e metadados for inválida
   */
  private void validateStatusConsistency() {
    if (status == UrlStatus.ACTIVE && (deletedAt != null || deletedBy != null)) {
      throw new IllegalArgumentException("Active URL cannot have delete metadata.");
    }

    if (status == UrlStatus.DELETED && (deletedAt == null || deletedBy == null)) {
      throw new IllegalArgumentException("Deleted URL requires delete metadata.");
    }
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

  @Override
  public boolean equals(Object object) {
    if (!(object instanceof Url url)) return false;
    return Objects.equals(shortCode, url.shortCode);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(shortCode);
  }
}
