package com.app.url_shortener.shared.outbox.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

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
    @DisplayName("Deve rejeitar versão de schema inválida")
    void shouldRejectInvalidSchemaVersion() {
      // 1. Arrange

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  OutboxEvent.restore(
                      UUID.randomUUID(),
                      AGGREGATE_TYPE,
                      AGGREGATE_ID,
                      EVENT_TYPE,
                      0,
                      PAYLOAD,
                      OutboxEventStatus.PENDING,
                      0,
                      null,
                      CREATED_AT,
                      null,
                      null));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("schemaVersion must be greater than zero");
    }

    @Test
    @DisplayName("Deve rejeitar quantidade de tentativas negativa")
    void shouldRejectNegativeAttempts() {
      // 1. Arrange

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  OutboxEvent.restore(
                      UUID.randomUUID(),
                      AGGREGATE_TYPE,
                      AGGREGATE_ID,
                      EVENT_TYPE,
                      1,
                      PAYLOAD,
                      OutboxEventStatus.PENDING,
                      -1,
                      null,
                      CREATED_AT,
                      null,
                      null));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("attempts must not be negative");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("com.app.url_shortener.shared.outbox.domain.model.OutboxEventTest#requiredFieldScenarios")
    @DisplayName("Deve rejeitar campos obrigatórios nulos")
    void shouldRejectNullRequiredFields(
        String scenario, Supplier<OutboxEvent> eventSupplier, String expectedMessage) {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(eventSupplier::get);

      // 3. Assert
      throwableAssert.isInstanceOf(NullPointerException.class).hasMessage(expectedMessage);
    }

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
      var event =
          OutboxEvent.createPending(
              eventId, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, CREATED_AT);
      var delay = Duration.ofMinutes(1);
      var failureAt = Instant.parse("2100-01-01T00:00:00Z");

      // 2. Act
      event.registerFailure("temporary failure", failureAt, 3, delay);

      // 3. Assert
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(event.getAttempts()).isEqualTo(1);
      assertThat(event.getLastError()).isEqualTo("temporary failure");
      assertThat(event.getNextAttemptAt()).isEqualTo(failureAt.plus(delay));
      assertThat(event.canBePublishedAt(failureAt)).isFalse();
    }

    @Test
    @DisplayName("Deve marcar evento como falho ao atingir o máximo de tentativas")
    void shouldMarkEventAsFailedWhenMaximumAttemptsIsReached() {
      // 1. Arrange
      var eventId = UUID.randomUUID();
      var event =
          OutboxEvent.createPending(
              eventId, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, CREATED_AT);

      // 2. Act
      event.registerFailure(
          "permanent failure", CREATED_AT.plusSeconds(30), 1, Duration.ofSeconds(30));

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
                      "another failure",
                      CREATED_AT.plusSeconds(60),
                      Integer.MAX_VALUE,
                      Duration.ofSeconds(30)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("attempts limit exceeded");
      assertThat(event.getAttempts()).isEqualTo(Integer.MAX_VALUE);
      assertThat(event.getLastError()).isEqualTo("temporary failure");
      assertThat(event.getNextAttemptAt()).isEqualTo(nextAttemptAt);
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   "})
    @DisplayName("Deve usar erro desconhecido quando a mensagem for nula ou estiver em branco")
    void shouldUseUnknownErrorWhenMessageIsNullOrBlank(String errorMessage) {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      event.registerFailure(errorMessage, CREATED_AT.plusSeconds(30), 1, Duration.ofSeconds(30));

      // 3. Assert
      assertThat(event.getLastError()).isEqualTo("Unknown error");
    }

    @Test
    @DisplayName("Deve limitar último erro ao tamanho máximo")
    void shouldTruncateLastErrorToMaximumLength() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      event.registerFailure(
          "x".repeat(2_001), CREATED_AT.plusSeconds(30), 1, Duration.ofSeconds(30));

      // 3. Assert
      assertThat(event.getLastError()).hasSize(2_000);
    }

    @Test
    @DisplayName("Deve rejeitar quantidade máxima de tentativas inválida")
    void shouldRejectInvalidMaximumAttempts() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  event.registerFailure(
                      "failure", CREATED_AT.plusSeconds(30), 0, Duration.ofSeconds(30)))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("maxAttempts must be greater than zero");
    }

    @Test
    @DisplayName("Deve rejeitar data da falha nula")
    void shouldRejectNullFailureDate() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () -> event.registerFailure("failure", null, 3, Duration.ofSeconds(30)));

      // 3. Assert
      throwableAssert.isInstanceOf(NullPointerException.class).hasMessage("now must not be null");
    }

    @Test
    @DisplayName("Deve rejeitar atraso de retry nulo, zero ou negativo")
    void shouldRejectInvalidRetryDelay() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () -> event.registerFailure("failure", CREATED_AT.plusSeconds(30), 3, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("nextAttemptDelay must not be null");
      assertThatThrownBy(
              () ->
                  event.registerFailure("failure", CREATED_AT.plusSeconds(30), 3, Duration.ZERO))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("nextAttemptDelay must be positive");
      assertThatThrownBy(
              () ->
                  event.registerFailure(
                      "failure", CREATED_AT.plusSeconds(30), 3, Duration.ofSeconds(-1)))
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
              () ->
                  published.registerFailure(
                      "failure", CREATED_AT.plusSeconds(60), 3, Duration.ofSeconds(30)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Only PENDING outbox events can register failure");
      assertThatThrownBy(
              () ->
                  failed.registerFailure(
                      "failure", CREATED_AT.plusSeconds(60), 3, Duration.ofSeconds(30)))
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
      var event =
          OutboxEvent.createPending(
              eventId, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, CREATED_AT);
      var publishedAt = CREATED_AT.plusSeconds(30);

      // 2. Act
      event.markAsPublished(publishedAt);

      // 3. Assert
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
      assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
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
      event.markAsPublished(CREATED_AT.plusSeconds(60));

      // 3. Assert
      assertThat(event.getPublishedAt()).isEqualTo(publishedAt);
    }

    @Test
    @DisplayName("Deve rejeitar publicação de evento falho")
    void shouldRejectPublishingFailedEvent() {
      // 1. Arrange
      var event = restore(OutboxEventStatus.FAILED, 1, "failure", null, null);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> event.markAsPublished(CREATED_AT.plusSeconds(60)))
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("FAILED outbox event cannot be marked as published");
    }

    @Test
    @DisplayName("Deve rejeitar data de publicação nula")
    void shouldRejectNullPublicationDate() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> event.markAsPublished(null));

      // 3. Assert
      throwableAssert.isInstanceOf(NullPointerException.class).hasMessage("now must not be null");
    }

    @Test
    @DisplayName("Deve rejeitar publicação anterior à criação sem alterar evento")
    void shouldRejectPublicationBeforeCreationWithoutChangingEvent() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> event.markAsPublished(CREATED_AT.minusSeconds(1)));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("publishedAt must not be before createdAt");
      assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
      assertThat(event.getPublishedAt()).isNull();
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
      var publishable = event.canBePublishedAt(CREATED_AT);

      // 3. Assert
      assertThat(publishable).isTrue();
    }

    @Test
    @DisplayName("Deve considerar retry elegível exatamente na data da próxima tentativa")
    void shouldConsiderRetryPublishableExactlyAtNextAttemptDate() {
      // 1. Arrange
      var nextAttemptAt = CREATED_AT.plusSeconds(30);
      var event =
          restore(OutboxEventStatus.PENDING, 1, "temporary failure", null, nextAttemptAt);

      // 2. Act
      var beforeNextAttempt = event.canBePublishedAt(nextAttemptAt.minusNanos(1));
      var atNextAttempt = event.canBePublishedAt(nextAttemptAt);
      var afterNextAttempt = event.canBePublishedAt(nextAttemptAt.plusNanos(1));

      // 3. Assert
      assertThat(beforeNextAttempt).isFalse();
      assertThat(atNextAttempt).isTrue();
      assertThat(afterNextAttempt).isTrue();
    }

    @Test
    @DisplayName("Não deve considerar eventos terminais elegíveis para publicação")
    void shouldNotConsiderTerminalEventsPublishable() {
      // 1. Arrange
      var published =
          restore(OutboxEventStatus.PUBLISHED, 1, null, CREATED_AT.plusSeconds(30), null);
      var failed = restore(OutboxEventStatus.FAILED, 1, "failure", null, null);

      // 2. Act
      var publishedEligible = published.canBePublishedAt(CREATED_AT.plusSeconds(60));
      var failedEligible = failed.canBePublishedAt(CREATED_AT.plusSeconds(60));

      // 3. Assert
      assertThat(publishedEligible).isFalse();
      assertThat(failedEligible).isFalse();
    }

    @Test
    @DisplayName("Deve rejeitar data de elegibilidade nula")
    void shouldRejectNullEligibilityDate() {
      // 1. Arrange
      var event = pendingEvent();

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> event.canBePublishedAt(null));

      // 3. Assert
      throwableAssert.isInstanceOf(NullPointerException.class).hasMessage("now must not be null");
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

  private static Stream<Arguments> requiredFieldScenarios() {
    return Stream.of(
        Arguments.of(
            "identificador",
            (Supplier<OutboxEvent>)
                () ->
                    OutboxEvent.createPending(
                        null, AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, CREATED_AT),
            "id must not be null"),
        Arguments.of(
            "tipo do agregado",
            (Supplier<OutboxEvent>)
                () ->
                    OutboxEvent.createPending(
                        UUID.randomUUID(), null, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, CREATED_AT),
            "aggregateType must not be null"),
        Arguments.of(
            "identificador do agregado",
            (Supplier<OutboxEvent>)
                () ->
                    OutboxEvent.createPending(
                        UUID.randomUUID(), AGGREGATE_TYPE, null, EVENT_TYPE, PAYLOAD, CREATED_AT),
            "aggregateId must not be null"),
        Arguments.of(
            "tipo do evento",
            (Supplier<OutboxEvent>)
                () ->
                    OutboxEvent.createPending(
                        UUID.randomUUID(), AGGREGATE_TYPE, AGGREGATE_ID, null, PAYLOAD, CREATED_AT),
            "eventType must not be null"),
        Arguments.of(
            "payload",
            (Supplier<OutboxEvent>)
                () ->
                    OutboxEvent.createPending(
                        UUID.randomUUID(), AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, null, CREATED_AT),
            "payload is required"),
        Arguments.of(
            "data de criação",
            (Supplier<OutboxEvent>)
                () ->
                    OutboxEvent.createPending(
                        UUID.randomUUID(), AGGREGATE_TYPE, AGGREGATE_ID, EVENT_TYPE, PAYLOAD, null),
            "createdAt must not be null"),
        Arguments.of(
            "status",
            (Supplier<OutboxEvent>)
                () ->
                    OutboxEvent.restore(
                        UUID.randomUUID(),
                        AGGREGATE_TYPE,
                        AGGREGATE_ID,
                        EVENT_TYPE,
                        1,
                        PAYLOAD,
                        null,
                        0,
                        null,
                        CREATED_AT,
                        null,
                        null),
            "status must not be null"));
  }
}
