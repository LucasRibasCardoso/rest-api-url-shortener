package com.app.url_shortener.iam.domain.model;

import com.app.url_shortener.iam.domain.enums.EmailDispatchPurpose;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import com.app.url_shortener.shared.domain.validation.RequiredText;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class EmailDispatch {

  @EqualsAndHashCode.Include private final UUID id;

  private final UUID eventId;
  private final UUID userId;
  private final UUID verificationTokenId;
  private final String email;
  private final EmailDispatchPurpose purpose;
  private final EmailDispatchReason reason;
  private final Instant createdAt;

  private EmailDispatchStatus status;
  private String providerMessageId;
  private int sendAttempts;
  private Instant sendingStartedAt;
  private Instant acceptedAt;
  private Instant failedAt;
  private String lastErrorCode;
  private String lastErrorMessage;
  private Instant updatedAt;

  private EmailDispatch(
      UUID id,
      UUID eventId,
      UUID userId,
      UUID verificationTokenId,
      String email,
      EmailDispatchPurpose purpose,
      EmailDispatchReason reason,
      EmailDispatchStatus status,
      String providerMessageId,
      int sendAttempts,
      Instant sendingStartedAt,
      Instant acceptedAt,
      Instant failedAt,
      String lastErrorCode,
      String lastErrorMessage,
      Instant createdAt,
      Instant updatedAt) {
    this.id = Objects.requireNonNull(id, "id is required");
    this.eventId = Objects.requireNonNull(eventId, "eventId is required");
    this.userId = Objects.requireNonNull(userId, "userId is required");
    this.verificationTokenId =
        Objects.requireNonNull(verificationTokenId, "verificationTokenId is required");
    this.email = RequiredText.normalize(email, "email");
    this.purpose = Objects.requireNonNull(purpose, "purpose is required");
    this.reason = Objects.requireNonNull(reason, "reason is required");
    this.status = Objects.requireNonNull(status, "status is required");
    this.providerMessageId = normalizeOptional(providerMessageId);
    this.sendAttempts = sendAttempts;
    this.sendingStartedAt = sendingStartedAt;
    this.acceptedAt = acceptedAt;
    this.failedAt = failedAt;
    this.lastErrorCode = normalizeOptional(lastErrorCode);
    this.lastErrorMessage = normalizeOptional(lastErrorMessage);
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt is required");
    this.updatedAt = Objects.requireNonNull(updatedAt, "updatedAt is required");

    validateAttempts();
    validateTimestamps();
    validateStatusConsistency();
  }

  public static EmailDispatch create(
      UUID eventId,
      UUID userId,
      UUID verificationTokenId,
      String email,
      EmailDispatchPurpose purpose,
      EmailDispatchReason reason,
      Instant now) {
    Instant timestamp = Objects.requireNonNull(now, "now is required");
    return new EmailDispatch(
        UUID.randomUUID(),
        eventId,
        userId,
        verificationTokenId,
        email,
        purpose,
        reason,
        EmailDispatchStatus.PENDING,
        null,
        0,
        null,
        null,
        null,
        null,
        null,
        timestamp,
        timestamp);
  }

  public static EmailDispatch restore(
      UUID id,
      UUID eventId,
      UUID userId,
      UUID verificationTokenId,
      String email,
      EmailDispatchPurpose purpose,
      EmailDispatchReason reason,
      EmailDispatchStatus status,
      String providerMessageId,
      int sendAttempts,
      Instant sendingStartedAt,
      Instant acceptedAt,
      Instant failedAt,
      String lastErrorCode,
      String lastErrorMessage,
      Instant createdAt,
      Instant updatedAt) {
    return new EmailDispatch(
        id,
        eventId,
        userId,
        verificationTokenId,
        email,
        purpose,
        reason,
        status,
        providerMessageId,
        sendAttempts,
        sendingStartedAt,
        acceptedAt,
        failedAt,
        lastErrorCode,
        lastErrorMessage,
        createdAt,
        updatedAt);
  }

  public boolean isFailed() {
    return status == EmailDispatchStatus.FAILED;
  }

  public boolean isPending() {
    return status == EmailDispatchStatus.PENDING;
  }

  public boolean isAccepted() {
    return status == EmailDispatchStatus.ACCEPTED;
  }

  public boolean isSending() {
    return status == EmailDispatchStatus.SENDING;
  }

  public boolean isSendingRecently(Instant now, Duration timeout) {
    Objects.requireNonNull(now, "now is required");
    Objects.requireNonNull(timeout, "timeout is required");

    if (timeout.isNegative() || timeout.isZero()) {
      return false;
    }

    if (!isSending() || sendingStartedAt == null) {
      return false;
    }

    return now.isBefore(sendingStartedAt.plus(timeout));
  }

  private static String normalizeOptional(String value) {
    if (value == null) {
      return null;
    }

    String normalizedValue = value.trim();
    return normalizedValue.isBlank() ? null : normalizedValue;
  }

  private void validateAttempts() {
    if (sendAttempts < 0) {
      throw new IllegalArgumentException("sendAttempts must not be negative");
    }
  }

  private void validateTimestamps() {
    if (updatedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("updatedAt must not be before createdAt");
    }

    if (sendingStartedAt != null && sendingStartedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("sendingStartedAt must not be before createdAt");
    }

    if (acceptedAt != null && acceptedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("acceptedAt must not be before createdAt");
    }

    if (failedAt != null && failedAt.isBefore(createdAt)) {
      throw new IllegalArgumentException("failedAt must not be before createdAt");
    }
  }

  private void validateStatusConsistency() {
    if (status == EmailDispatchStatus.PENDING
        && (sendingStartedAt != null
            || acceptedAt != null
            || failedAt != null
            || providerMessageId != null
            || lastErrorCode != null
            || lastErrorMessage != null)) {
      throw new IllegalArgumentException("Pending email dispatch cannot have processing metadata");
    }

    if (status == EmailDispatchStatus.SENDING && sendingStartedAt == null) {
      throw new IllegalArgumentException("Sending email dispatch requires sendingStartedAt");
    }

    if (status == EmailDispatchStatus.ACCEPTED
        && (acceptedAt == null
            || providerMessageId == null
            || failedAt != null
            || lastErrorCode != null
            || lastErrorMessage != null)) {
      throw new IllegalArgumentException("Accepted email dispatch requires acceptance metadata");
    }

    if (status == EmailDispatchStatus.FAILED
        && (failedAt == null
            || lastErrorCode == null
            || acceptedAt != null
            || providerMessageId != null)) {
      throw new IllegalArgumentException("Failed email dispatch requires failure metadata");
    }
  }

  @Override
  public String toString() {
    return "EmailDispatch{"
        + "id="
        + id
        + ", eventId="
        + eventId
        + ", userId="
        + userId
        + ", verificationTokenId="
        + verificationTokenId
        + ", email='"
        + email
        + '\''
        + ", purpose='"
        + purpose
        + '\''
        + ", reason='"
        + reason
        + '\''
        + ", status="
        + status
        + ", sendAttempts="
        + sendAttempts
        + ", createdAt="
        + createdAt
        + ", updatedAt="
        + updatedAt
        + '}';
  }
}
