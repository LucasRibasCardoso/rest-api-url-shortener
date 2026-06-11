package com.app.url_shortener.shared.outbox.infrastructure.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.app.url_shortener.shared.outbox.domain.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Entidade OutboxEvent")
class OutboxEventTest {

  private static final OutboxAggregateType AGGREGATE_TYPE = OutboxAggregateType.of("USER");
  private static final OutboxAggregateId AGGREGATE_ID = OutboxAggregateId.of("user-123");
  private static final OutboxEventType EVENT_TYPE =
      OutboxEventType.of("EMAIL_VERIFICATION_REQUESTED");
  private static final String PAYLOAD = "{\"userId\":\"user-123\"}";
  private static final Instant CREATED_AT = Instant.parse("2026-06-10T10:00:00Z");

  @Nested
  @DisplayName("Criação")
  class CreationTests {

    @Test
    @DisplayName("Deve criar evento pendente sem metadados de processamento")
    void shouldCreatePendingEventWithoutProcessingMetadata() {
      // 1. Arrange
      var eventId = UUID.randomUUID();

      // 2. Act
      var event =
          OutboxEvent.createPending(
              eventId, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, CREATED_AT);

      // 3. Assert
      assertThat(event.getId()).isEqualTo(eventId);
      assertThat(event.getAggregateType()).isEqualTo(AGGREGATE_TYPE);
      assertThat(event.getAggregateId()).isEqualTo(AGGREGATE_ID);
      assertThat(event.getEventType()).isEqualTo(EVENT_TYPE);
      assertThat(event.getSchemaVersion()).isEqualTo(1);
      assertThat(event.getPayload()).isEqualTo(PAYLOAD);
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(event.getAttempts()).isZero();
      assertThat(event.getLastError()).isNull();
      assertThat(event.getPublishedAt()).isNull();
      assertThat(event.getNextAttemptAt()).isNull();
      assertThat(event.getCreatedAt()).isEqualTo(CREATED_AT);
    }
  }

  @Nested
  @DisplayName("Restauração")
  class RestorationTests {

    @Test
    @DisplayName("Deve rejeitar último erro em branco")
    void shouldRejectBlankLastError() {
      // 1. Arrange
      var nextAttemptAt = CREATED_AT.plusSeconds(30);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      OutboxEventStatus.PENDING, 1, "   ", null, nextAttemptAt))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("lastError must not be blank");
    }

    @Test
    @DisplayName("Deve rejeitar último erro acima do tamanho máximo")
    void shouldRejectOversizedLastError() {
      // 1. Arrange
      var oversizedError = "x".repeat(2_001);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      OutboxEventStatus.FAILED, 1, oversizedError, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("lastError must not exceed 2000 characters");
    }

    @Test
    @DisplayName("Deve rejeitar evento falho sem último erro")
    void shouldRejectFailedEventWithoutLastError() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () -> restore(OutboxEventStatus.FAILED, 1, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("FAILED event must have lastError");
    }

    @Test
    @DisplayName("Deve restaurar estados válidos")
    void shouldRestoreValidStates() {
      // 1. Arrange
      var publishedAt = CREATED_AT.plusSeconds(30);
      var nextAttemptAt = CREATED_AT.plusSeconds(60);

      // 2. Act
      var initialPending = restore(OutboxEventStatus.PENDING, 0, null, null, null);
      var retryPending =
          restore(OutboxEventStatus.PENDING, 1, "temporary failure", null, nextAttemptAt);
      var published = restore(OutboxEventStatus.PUBLISHED, 1, null, publishedAt, null);
      var failed = restore(OutboxEventStatus.FAILED, 3, "permanent failure", null, null);

      // 3. Assert
      assertThat(initialPending.isPending()).isTrue();
      assertThat(retryPending.isPending()).isTrue();
      assertThat(published.isPublished()).isTrue();
      assertThat(failed.isFailed()).isTrue();
    }

    @Test
    @DisplayName("Deve rejeitar evento pendente inicial com metadados de retry")
    void shouldRejectInitialPendingEventWithRetryMetadata() {
      // 1. Arrange
      var nextAttemptAt = CREATED_AT.plusSeconds(30);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      OutboxEventStatus.PENDING, 0, "temporary failure", null, nextAttemptAt))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Initial PENDING event must not have retry metadata");
    }

    @Test
    @DisplayName("Deve rejeitar evento pendente em retry sem todos os metadados")
    void shouldRejectRetriedPendingEventWithoutCompleteRetryMetadata() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () -> restore(OutboxEventStatus.PENDING, 1, "temporary failure", null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Retried PENDING event must have lastError and nextAttemptAt");
    }

    @Test
    @DisplayName("Deve rejeitar evento publicado sem data de publicação")
    void shouldRejectPublishedEventWithoutPublishedAt() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> restore(OutboxEventStatus.PUBLISHED, 0, null, null, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("PUBLISHED event must have publishedAt");
    }

    @Test
    @DisplayName("Deve rejeitar publicação anterior à criação")
    void shouldRejectPublishedAtBeforeCreatedAt() {
      // 1. Arrange
      var publishedAt = CREATED_AT.minusSeconds(1);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () -> restore(OutboxEventStatus.PUBLISHED, 0, null, publishedAt, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("publishedAt must not be before createdAt");
    }

    @Test
    @DisplayName("Deve rejeitar evento falho com próxima tentativa")
    void shouldRejectFailedEventWithNextAttemptAt() {
      // 1. Arrange
      var nextAttemptAt = CREATED_AT.plusSeconds(30);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  restore(
                      OutboxEventStatus.FAILED, 1, "permanent failure", null, nextAttemptAt))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("FAILED event must not have nextAttemptAt");
    }
  }

  @Nested
  @DisplayName("Registro de falha")
  class FailureRegistrationTests {

    @Test
    @DisplayName("Deve agendar nova tentativa após falha não terminal")
    void shouldScheduleRetryAfterNonTerminalFailure() {
      // 1. Arrange
      var eventId = UUID.randomUUID();
      var event = OutboxEvent.createPending(eventId, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, Instant.now());
      var delay = Duration.ofMinutes(1);
      var beforeRegistration = Instant.now().plus(delay);

      // 2. Act
      event.registerFailure("temporary failure", 3, delay);

      // 3. Assert
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(event.getAttempts()).isEqualTo(1);
      assertThat(event.getLastError()).isEqualTo("temporary failure");
      assertThat(event.getNextAttemptAt()).isAfterOrEqualTo(beforeRegistration);
      assertThat(event.canBePublishedAt()).isFalse();
    }

    @Test
    @DisplayName("Deve marcar evento como falho ao atingir o máximo de tentativas")
    void shouldMarkEventAsFailedWhenMaximumAttemptsIsReached() {
      // 1. Arrange
      var eventId = UUID.randomUUID();
      var event = OutboxEvent.createPending(eventId ,AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, Instant.now());

      // 2. Act
      event.registerFailure("permanent failure", 1, Duration.ofSeconds(30));

      // 3. Assert
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
      assertThat(event.getAttempts()).isEqualTo(1);
      assertThat(event.getLastError()).isEqualTo("permanent failure");
      assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("Não deve alterar evento quando o contador de tentativas exceder o limite")
    void shouldNotChangeEventWhenAttemptsCounterOverflows() {
      // 1. Arrange
      var nextAttemptAt = CREATED_AT.plusSeconds(30);
      var event =
          restore(
              OutboxEventStatus.PENDING,
              Integer.MAX_VALUE,
              "temporary failure",
              null,
              nextAttemptAt);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  event.registerFailure(
                      "another failure", Integer.MAX_VALUE, Duration.ofSeconds(30)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("attempts limit exceeded");
      assertThat(event.getAttempts()).isEqualTo(Integer.MAX_VALUE);
      assertThat(event.getLastError()).isEqualTo("temporary failure");
      assertThat(event.getNextAttemptAt()).isEqualTo(nextAttemptAt);
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @Test
    @DisplayName("Deve usar erro desconhecido quando a mensagem estiver em branco")
    void shouldUseUnknownErrorWhenMessageIsBlank() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      event.registerFailure("   ", 1, Duration.ofSeconds(30));

      // 3. Assert
      assertThat(event.getLastError()).isEqualTo("Unknown error");
    }

    @Test
    @DisplayName("Deve limitar último erro ao tamanho máximo")
    void shouldTruncateLastErrorToMaximumLength() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      event.registerFailure("x".repeat(2_001), 1, Duration.ofSeconds(30));

      // 3. Assert
      assertThat(event.getLastError()).hasSize(2_000);
    }

    @Test
    @DisplayName("Deve rejeitar quantidade máxima de tentativas inválida")
    void shouldRejectInvalidMaximumAttempts() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> event.registerFailure("failure", 0, Duration.ofSeconds(30)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("maxAttempts must be greater than zero");
    }

    @Test
    @DisplayName("Deve rejeitar atraso de retry nulo, zero ou negativo")
    void shouldRejectInvalidRetryDelay() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> event.registerFailure("failure", 3, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("nextAttemptDelay must not be null");
      assertThatThrownBy(() -> event.registerFailure("failure", 3, Duration.ZERO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("nextAttemptDelay must be positive");
      assertThatThrownBy(() -> event.registerFailure("failure", 3, Duration.ofSeconds(-1)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("nextAttemptDelay must be positive");
    }

    @Test
    @DisplayName("Deve rejeitar registro de falha em evento publicado ou falho")
    void shouldRejectFailureRegistrationForTerminalEvent() {
      // 1. Arrange
      var published =
          restore(OutboxEventStatus.PUBLISHED, 1, null, CREATED_AT.plusSeconds(30), null);
      var failed = restore(OutboxEventStatus.FAILED, 1, "failure", null, null);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () -> published.registerFailure("failure", 3, Duration.ofSeconds(30)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Only PENDING outbox events can register failure");
      assertThatThrownBy(() -> failed.registerFailure("failure", 3, Duration.ofSeconds(30)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Only PENDING outbox events can register failure");
    }
  }

  @Nested
  @DisplayName("Publicação")
  class PublicationTests {

    @Test
    @DisplayName("Deve marcar evento pendente como publicado")
    void shouldMarkPendingEventAsPublished() {
      // 1. Arrange
      var eventId = UUID.randomUUID();
      var event = OutboxEvent.createPending(eventId, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, Instant.now());
      var beforePublication = Instant.now();

      // 2. Act
      event.markAsPublished();

      // 3. Assert
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
      assertThat(event.getPublishedAt()).isAfterOrEqualTo(beforePublication);
      assertThat(event.getLastError()).isNull();
      assertThat(event.getNextAttemptAt()).isNull();
    }

    @Test
    @DisplayName("Deve manter publicação existente ao publicar novamente")
    void shouldKeepExistingPublicationWhenPublishingAgain() {
      // 1. Arrange
      var publishedAt = CREATED_AT.plusSeconds(30);
      var event = restore(OutboxEventStatus.PUBLISHED, 1, null, publishedAt, null);

      // 2. Act
      event.markAsPublished();

      // 3. Assert
      assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("Deve rejeitar publicação de evento falho")
    void shouldRejectPublishingFailedEvent() {
      // 1. Arrange
      var event = restore(OutboxEventStatus.FAILED, 1, "failure", null, null);

      // 2. Act & 3. Assert
      assertThatThrownBy(event::markAsPublished)
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("FAILED outbox event cannot be marked as published");
    }
  }

  @Nested
  @DisplayName("Elegibilidade e igualdade")
  class EligibilityAndEqualityTests {

    @Test
    @DisplayName("Deve considerar pendente inicial elegível para publicação")
    void shouldConsiderInitialPendingEventPublishable() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      var publishable = event.canBePublishedAt();

      // 3. Assert
      assertThat(publishable).isTrue();
    }

    @Test
    @DisplayName("Deve considerar eventos iguais quando possuírem o mesmo identificador")
    void shouldBeEqualWhenIdIsEqual() {
      // 1. Arrange
      var id = UUID.randomUUID();
      var first =
          OutboxEvent.createPending(id, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, CREATED_AT);
      var second =
          OutboxEvent.createPending(id, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, "other", CREATED_AT);

      // 2. Act & 3. Assert
      assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
    }
  }

  private static OutboxEvent pendingEvent() {
    return OutboxEvent.createPending(
        UUID.randomUUID(), AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, CREATED_AT);
  }

  private static OutboxEvent restore(
      OutboxEventStatus status,
      int attempts,
      String lastError,
      Instant publishedAt,
      Instant nextAttemptAt) {
    return OutboxEvent.restore(
        UUID.randomUUID(),
        AGGREGATE_TYPE,
        AGGREGATE_ID,
        EVENT_TYPE,
        1,
        PAYLOAD,
        status,
        attempts,
        lastError,
        CREATED_AT,
        publishedAt,
        nextAttemptAt);
  }
}
