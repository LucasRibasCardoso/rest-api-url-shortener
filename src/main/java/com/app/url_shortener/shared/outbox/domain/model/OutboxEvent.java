package com.app.url_shortener.shared.outbox.domain.model;

import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class OutboxEvent {

  private static final int DEFAULT_SCHEMA_VERSION = 1;
  private static final int MAX_LAST_ERROR_LENGTH = 2_000;

  @EqualsAndHashCode.Include private final UUID id;
  private final OutboxAggregateType aggregateType;
  private final OutboxAggregateId aggregateId;
  private final OutboxEventType eventType;
  private final int schemaVersion;
  private final String payload;
  private OutboxEventStatus status;
  private int attempts;
  private String lastError;
  private final Instant createdAt;
  private Instant publishedAt;
  private Instant nextAttemptAt;

  private OutboxEvent(
      UUID id,
      OutboxAggregateType aggregateType,
      OutboxAggregateId aggregateId,
      OutboxEventType eventType,
      int schemaVersion,
      String payload,
      OutboxEventStatus status,
      int attempts,
      String lastError,
      Instant createdAt,
      Instant publishedAt,
      Instant nextAttemptAt) {

    this.id = Objects.requireNonNull(id, "id must not be null");
    this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType must not be null");
    this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId must not be null");
    this.eventType = Objects.requireNonNull(eventType, "eventType must not be null");
    this.schemaVersion = validateSchemaVersion(schemaVersion);
    this.payload = RequiredText.normalize(payload, "payload");
    this.status = Objects.requireNonNull(status, "status must not be null");
    this.attempts = validateAttempts(attempts);
    this.lastError = validateLastError(lastError);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    this.publishedAt = publishedAt;
    this.nextAttemptAt = nextAttemptAt;
    validateState();
  }

  public static OutboxEvent createPending(
      UUID id,
      OutboxAggregateType aggregateType,
      OutboxAggregateId aggregateId,
      OutboxEventType eventType,
      String payload,
      Instant createdAt) {

    return new OutboxEvent(
        id,
        aggregateType,
        aggregateId,
        eventType,
        DEFAULT_SCHEMA_VERSION,
        payload,
        OutboxEventStatus.PENDING,
        0,
        null,
        createdAt,
        null,
        null);
  }

  public static OutboxEvent restore(
      UUID id,
      OutboxAggregateType aggregateType,
      OutboxAggregateId aggregateId,
      OutboxEventType eventType,
      int schemaVersion,
      String payload,
      OutboxEventStatus status,
      int attempts,
      String lastError,
      Instant createdAt,
      Instant publishedAt,
      Instant nextAttemptAt) {

    return new OutboxEvent(
        id,
        aggregateType,
        aggregateId,
        eventType,
        schemaVersion,
        payload,
        status,
        attempts,
        lastError,
        createdAt,
        publishedAt,
        nextAttemptAt);
  }

  public boolean isPending() {
    return status == OutboxEventStatus.PENDING;
  }

  public boolean isPublished() {
    return status == OutboxEventStatus.PUBLISHED;
  }

  public boolean isFailed() {
    return status == OutboxEventStatus.FAILED;
  }

  public boolean canBePublishedAt() {
    Instant now = Instant.now();
    return status == OutboxEventStatus.PENDING
        && (nextAttemptAt == null || !nextAttemptAt.isAfter(now));
  }

  public void markAsPublished() {
    Instant now = Instant.now();
    if (status == OutboxEventStatus.PUBLISHED) {
      return;
    }

    if (status == OutboxEventStatus.FAILED) {
      throw new IllegalStateException("FAILED outbox event cannot be marked as published");
    }

    this.status = OutboxEventStatus.PUBLISHED;
    this.publishedAt = now;
    this.nextAttemptAt = null;
    this.lastError = null;
  }

  public void registerFailure(String errorMessage, int maxAttempts, Duration nextAttemptDelay) {
    Objects.requireNonNull(nextAttemptDelay, "nextAttemptDelay must not be null");

    if (status != OutboxEventStatus.PENDING) {
      throw new IllegalStateException("Only PENDING outbox events can register failure");
    }

    if (maxAttempts <= 0) {
      throw new IllegalArgumentException("maxAttempts must be greater than zero");
    }

    if (nextAttemptDelay.isNegative() || nextAttemptDelay.isZero()) {
      throw new IllegalArgumentException("nextAttemptDelay must be positive");
    }

    int updatedAttempts;
    try {
      updatedAttempts = Math.addExact(attempts, 1);
    } catch (ArithmeticException exception) {
      throw new IllegalStateException("attempts limit exceeded", exception);
    }
    String normalizedError = normalizeError(errorMessage);

    if (updatedAttempts >= maxAttempts) {
      this.attempts = updatedAttempts;
      this.lastError = normalizedError;
      this.status = OutboxEventStatus.FAILED;
      this.nextAttemptAt = null;
      return;
    }

    Instant scheduledAt = Instant.now().plus(nextAttemptDelay);

    this.attempts = updatedAttempts;
    this.lastError = normalizedError;
    this.status = OutboxEventStatus.PENDING;
    this.nextAttemptAt = scheduledAt;
  }

  private void validateState() {
    switch (status) {
      case PENDING -> validatePendingState();
      case PUBLISHED -> validatePublishedState();
      case FAILED -> validateFailedState();
    }
  }

  private void validatePendingState() {
    if (publishedAt != null) {
      throw new IllegalArgumentException("PENDING event must not have publishedAt");
    }

    if (attempts == 0 && (lastError != null || nextAttemptAt != null)) {
      throw new IllegalArgumentException("Initial PENDING event must not have retry metadata");
    }

    if (attempts > 0 && (lastError == null || nextAttemptAt == null)) {
      throw new IllegalArgumentException(
          "Retried PENDING event must have lastError and nextAttemptAt");
    }
  }

  private void validatePublishedState() {
    if (publishedAt == null) {
      throw new IllegalArgumentException("PUBLISHED event must have publishedAt");
    }

    if (publishedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("publishedAt must not be before createdAt");
    }

    if (lastError != null || nextAttemptAt != null) {
      throw new IllegalArgumentException("PUBLISHED event must not have retry metadata");
    }
  }

  private void validateFailedState() {
    if (publishedAt != null) {
      throw new IllegalArgumentException("FAILED event must not have publishedAt");
    }

    if (attempts <= 0) {
      throw new IllegalArgumentException("FAILED event must have at least one attempt");
    }

    if (lastError == null) {
      throw new IllegalArgumentException("FAILED event must have lastError");
    }

    if (nextAttemptAt != null) {
      throw new IllegalArgumentException("FAILED event must not have nextAttemptAt");
    }
  }

  private static int validateAttempts(int attempts) {
    if (attempts < 0) {
      throw new IllegalArgumentException("attempts must not be negative");
    }
    return attempts;
  }

  private static String validateLastError(String lastError) {
    if (lastError == null) {
      return null;
    }

    if (lastError.isBlank()) {
      throw new IllegalArgumentException("lastError must not be blank");
    }

    if (lastError.length() > MAX_LAST_ERROR_LENGTH) {
      throw new IllegalArgumentException(
          "lastError must not exceed " + MAX_LAST_ERROR_LENGTH + " characters");
    }

    return lastError;
  }

  private static int validateSchemaVersion(int schemaVersion) {
    if (schemaVersion <= 0) {
      throw new IllegalArgumentException("schemaVersion must be greater than zero");
    }
    return schemaVersion;
  }

  private static String normalizeError(String errorMessage) {
    if (errorMessage == null || errorMessage.isBlank()) {
      return "Unknown error";
    }

    return errorMessage.length() <= MAX_LAST_ERROR_LENGTH
        ? errorMessage
        : errorMessage.substring(0, MAX_LAST_ERROR_LENGTH);
  }
}
