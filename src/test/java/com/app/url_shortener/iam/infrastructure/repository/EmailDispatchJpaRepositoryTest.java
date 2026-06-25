package com.app.url_shortener.iam.infrastructure.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.config.BaseDataJpaSliceTest;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@Tag("jpa-slice")
@DisplayName("Slice Data JPA - Repositório de dispatch de e-mail")
class EmailDispatchJpaRepositoryTest extends BaseDataJpaSliceTest {

  private static final Instant CREATED_AT = Instant.parse("2026-06-18T10:00:00Z");
  private static final Instant NOW = CREATED_AT.plusSeconds(600);

  @Autowired private EmailDispatchJpaRepository repository;

  @Autowired private JdbcTemplate jdbcTemplate;

  @Nested
  @DisplayName("Reserva para envio")
  class SendingReservationTests {

    @Test
    @DisplayName("Deve reservar dispatch pendente, falho e em envio stale")
    void shouldReservePendingFailedAndStaleSendingDispatches() {
      // 1. Arrange
      UUID pendingId = insertDispatch("pending", EmailDispatchStatus.PENDING, 0, null, null);
      UUID failedId =
          insertDispatch(
              "failed",
              EmailDispatchStatus.FAILED,
              1,
              CREATED_AT.plusSeconds(120),
              CREATED_AT.plusSeconds(120));
      UUID staleSendingId =
          insertDispatch(
              "stale-sending",
              EmailDispatchStatus.SENDING,
              1,
              CREATED_AT.plusSeconds(120),
              null);
      Instant staleThreshold = CREATED_AT.plusSeconds(300);

      // 2. Act
      boolean pendingReserved =
          repository.markAsSendingIfAvailable(pendingId, NOW, staleThreshold) == 1;
      boolean failedReserved =
          repository.markAsSendingIfAvailable(failedId, NOW, staleThreshold) == 1;
      boolean staleSendingReserved =
          repository.markAsSendingIfAvailable(staleSendingId, NOW, staleThreshold) == 1;

      // 3. Assert
      assertThat(pendingReserved).isTrue();
      assertThat(failedReserved).isTrue();
      assertThat(staleSendingReserved).isTrue();
      assertSendingState(pendingId, 1);
      assertSendingState(failedId, 2);
      assertSendingState(staleSendingId, 2);
    }

    @Test
    @DisplayName("Deve rejeitar dispatch em envio recente e já aceito")
    void shouldRejectFreshSendingAndAcceptedDispatches() {
      // 1. Arrange
      UUID freshSendingId =
          insertDispatch(
              "fresh-sending",
              EmailDispatchStatus.SENDING,
              1,
              CREATED_AT.plusSeconds(500),
              null);
      UUID acceptedId =
          insertDispatch(
              "accepted",
              EmailDispatchStatus.ACCEPTED,
              1,
              CREATED_AT.plusSeconds(120),
              null);
      Instant staleThreshold = CREATED_AT.plusSeconds(300);

      // 2. Act
      int freshUpdated =
          repository.markAsSendingIfAvailable(freshSendingId, NOW, staleThreshold);
      int acceptedUpdated =
          repository.markAsSendingIfAvailable(acceptedId, NOW, staleThreshold);

      // 3. Assert
      assertThat(freshUpdated).isZero();
      assertThat(acceptedUpdated).isZero();
      assertThat(status(freshSendingId)).isEqualTo(EmailDispatchStatus.SENDING.name());
      assertThat(status(acceptedId)).isEqualTo(EmailDispatchStatus.ACCEPTED.name());
      assertThat(sendAttempts(freshSendingId)).isEqualTo(1);
      assertThat(sendAttempts(acceptedId)).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Finalização do envio")
  class SendingCompletionTests {

    @Test
    @DisplayName("Deve aceitar ou falhar somente dispatch em estado SENDING")
    void shouldAcceptOrFailOnlySendingDispatches() {
      // 1. Arrange
      UUID acceptedId =
          insertDispatch(
              "sending-to-accepted",
              EmailDispatchStatus.SENDING,
              1,
              CREATED_AT.plusSeconds(120),
              null);
      UUID failedId =
          insertDispatch(
              "sending-to-failed",
              EmailDispatchStatus.SENDING,
              1,
              CREATED_AT.plusSeconds(120),
              null);
      UUID pendingId = insertDispatch("pending-finish", EmailDispatchStatus.PENDING, 0, null, null);

      // 2. Act
      int accepted = repository.markAsAccepted(acceptedId, "provider-message-id", NOW);
      int failed = repository.markAsFailed(failedId, "SEND_FAILED", "SesException", NOW);
      int pendingAccepted = repository.markAsAccepted(pendingId, "unexpected-message-id", NOW);
      int pendingFailed = repository.markAsFailed(pendingId, "UNEXPECTED", "Unexpected", NOW);

      // 3. Assert
      assertThat(accepted).isEqualTo(1);
      assertThat(failed).isEqualTo(1);
      assertThat(pendingAccepted).isZero();
      assertThat(pendingFailed).isZero();
      assertThat(status(acceptedId)).isEqualTo(EmailDispatchStatus.ACCEPTED.name());
      assertThat(status(failedId)).isEqualTo(EmailDispatchStatus.FAILED.name());
      assertThat(status(pendingId)).isEqualTo(EmailDispatchStatus.PENDING.name());
      assertThat(stringColumn(acceptedId, "provider_message_id")).isEqualTo("provider-message-id");
      assertThat(stringColumn(failedId, "last_error_code")).isEqualTo("SEND_FAILED");
    }
  }

  private UUID insertDispatch(
      String discriminator,
      EmailDispatchStatus status,
      int attempts,
      Instant sendingStartedAt,
      Instant failedAt) {
    UUID userId = UUID.nameUUIDFromBytes((discriminator + "-user").getBytes());
    UUID tokenId = UUID.nameUUIDFromBytes((discriminator + "-token").getBytes());
    UUID dispatchId = UUID.nameUUIDFromBytes((discriminator + "-dispatch").getBytes());
    UUID eventId = UUID.nameUUIDFromBytes((discriminator + "-event").getBytes());
    String email = discriminator + "@email.com";

    jdbcTemplate.update(
        """
        INSERT INTO users (id, name, email, password_hash, status, plan, email_verified)
        VALUES (?, ?, ?, ?, ?, ?, ?)
        """,
        userId,
        "Dispatch User",
        email,
        "password-hash",
        "PENDING_EMAIL_VERIFICATION",
        "FREE",
        false);
    jdbcTemplate.update(
        """
        INSERT INTO email_verification_tokens (
            id, user_id, email, verification_code_hash, encrypted_code, expires_at,
            failed_attempts, created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        tokenId,
        userId,
        email,
        "verification-code-hash",
        "encrypted-code",
        timestamp(CREATED_AT.plusSeconds(3600)),
        0,
        timestamp(CREATED_AT),
        timestamp(CREATED_AT));
    jdbcTemplate.update(
        """
        INSERT INTO email_dispatches (
            id, event_id, user_id, verification_token_id, email, purpose, reason,
            status, provider_message_id, send_attempts, sending_started_at,
            accepted_at, failed_at, last_error_code, last_error_message,
            created_at, updated_at
        )
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        dispatchId,
        eventId,
        userId,
        tokenId,
        email,
        "EMAIL_VERIFICATION",
        "REGISTER",
        status.name(),
        status == EmailDispatchStatus.ACCEPTED ? "accepted-message-id" : null,
        attempts,
        timestamp(sendingStartedAt),
        status == EmailDispatchStatus.ACCEPTED
            ? timestamp(CREATED_AT.plusSeconds(180))
            : null,
        timestamp(failedAt),
        status == EmailDispatchStatus.FAILED ? "PREVIOUS_ERROR" : null,
        status == EmailDispatchStatus.FAILED ? "Previous failure" : null,
        timestamp(CREATED_AT),
        timestamp(
            sendingStartedAt == null && failedAt == null
                ? CREATED_AT
                : latest(sendingStartedAt, failedAt)));
    return dispatchId;
  }

  private void assertSendingState(UUID dispatchId, int expectedAttempts) {
    assertThat(status(dispatchId)).isEqualTo(EmailDispatchStatus.SENDING.name());
    assertThat(sendAttempts(dispatchId)).isEqualTo(expectedAttempts);
    assertThat(timestampColumn(dispatchId, "sending_started_at")).isEqualTo(NOW);
    assertThat(timestampColumn(dispatchId, "failed_at")).isNull();
    assertThat(stringColumn(dispatchId, "last_error_code")).isNull();
    assertThat(stringColumn(dispatchId, "last_error_message")).isNull();
  }

  private String status(UUID dispatchId) {
    return stringColumn(dispatchId, "status");
  }

  private Integer sendAttempts(UUID dispatchId) {
    return jdbcTemplate.queryForObject(
        "SELECT send_attempts FROM email_dispatches WHERE id = ?", Integer.class, dispatchId);
  }

  private String stringColumn(UUID dispatchId, String columnName) {
    return jdbcTemplate.queryForObject(
        "SELECT " + columnName + " FROM email_dispatches WHERE id = ?",
        String.class,
        dispatchId);
  }

  private Instant timestampColumn(UUID dispatchId, String columnName) {
    return jdbcTemplate.queryForObject(
        "SELECT " + columnName + " FROM email_dispatches WHERE id = ?",
        Instant.class,
        dispatchId);
  }

  private static Instant latest(Instant first, Instant second) {
    if (first == null) {
      return second;
    }
    if (second == null) {
      return first;
    }
    return first.isAfter(second) ? first : second;
  }

  private static Timestamp timestamp(Instant instant) {
    return instant == null ? null : Timestamp.from(instant);
  }
}
