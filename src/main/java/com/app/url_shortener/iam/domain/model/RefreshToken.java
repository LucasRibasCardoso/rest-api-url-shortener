package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class RefreshToken {

  private static final Duration EXPIRATION_DURATION = Duration.ofDays(7);

  @EqualsAndHashCode.Include private final UUID id;

  private final UUID userId;

  private final String tokenHash;
  private final Instant createdAt;
  private final Instant expiresAt;
  private final Instant revokedAt;
  private final UUID replacedByTokenId;

  private RefreshToken(
      UUID id,
      UUID userId,
      String tokenHash,
      Instant createdAt,
      Instant expiresAt,
      Instant revokedAt,
      UUID replacedByTokenId) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.userId = Objects.requireNonNull(userId, "userId is required");
    this.tokenHash = RequiredText.normalize(tokenHash, "tokenHash");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    this.expiresAt = Objects.requireNonNull(expiresAt, "expiresAt is required");
    this.revokedAt = revokedAt;
    this.replacedByTokenId = replacedByTokenId;
  }

  public static RefreshToken create(UUID userId, String tokenHash) {
    Instant createdAt = Instant.now();
    return new RefreshToken(
        UUID.randomUUID(),
        userId,
        tokenHash,
        createdAt,
        createdAt.plus(EXPIRATION_DURATION),
        null,
        null);
  }

  public static RefreshToken restore(
      UUID id,
      UUID userId,
      String tokenHash,
      Instant createdAt,
      Instant expiresAt,
      Instant revokedAt,
      UUID replacedByTokenId) {
    return new RefreshToken(
        id, userId, tokenHash, createdAt, expiresAt, revokedAt, replacedByTokenId);
  }

  public boolean isExpired() {
    return !Instant.now().isBefore(expiresAt);
  }

  public boolean isRevoked() {
    return revokedAt != null;
  }

  @Override
  public String toString() {

    return "RefreshToken{"
        + "id="
        + id
        + ", userId="
        + userId
        + ", createdAt="
        + createdAt
        + ", expiresAt="
        + expiresAt
        + ", revokedAt="
        + revokedAt
        + ", replacedByTokenId="
        + replacedByTokenId
        + '}';
  }
}
