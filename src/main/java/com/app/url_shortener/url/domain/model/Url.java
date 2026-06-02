package com.app.url_shortener.url.domain.model;

import java.io.Serializable;
import java.time.Instant;
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
  private final Instant createdAt;
  private UrlStatus status;
  private Instant deletedAt;
  private UUID deletedBy;
  private Instant updatedAt;

  private Url(
      UUID userId,
      String shortCode,
      String originalUrl,
      Instant createdAt,
      UrlStatus urlStatus,
      Instant deletedAt,
      UUID deletedBy,
      Instant updatedAt) {
    this.userId = Objects.requireNonNull(userId, "userId is required.");
    this.shortCode = Objects.requireNonNull(shortCode, "shortCode is required.").trim();
    this.originalUrl = Objects.requireNonNull(originalUrl, "originalUrl is required.").trim();
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt is  required");
    this.status = Objects.requireNonNull(urlStatus, "urlStatus is required.");
    this.deletedAt = deletedAt;
    this.deletedBy = deletedBy;
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required.");

    validateStatusConsistency();
  }

  public static Url create(UUID userId, String shortCode, String originalUrl) {
    Instant now = Instant.now();
    return new Url(userId, shortCode, originalUrl, now, UrlStatus.ACTIVE, null, null, now);
  }

  public static Url restore(
      UUID userId,
      String shortCode,
      String originalUrl,
      Instant createdAt,
      UrlStatus urlStatus,
      Instant deletedAt,
      UUID deletedBy,
      Instant updatedAt) {
    return new Url(
        userId,
        shortCode,
        originalUrl,
        createdAt,
        urlStatus,
        deletedAt,
        deletedBy,
        updatedAt);
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

  public void softDelete(Instant deletedAt, UUID deletedBy) {
    if (isDeleted()) {
      return;
    }

    this.status = UrlStatus.DELETED;
    this.deletedAt = Objects.requireNonNull(deletedAt, "deletedAt is required.");
    this.deletedBy = Objects.requireNonNull(deletedBy, "deletedBy is required.");
    this.updatedAt = Objects.requireNonNull(deletedAt, "updatedAt is required.");
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
}
