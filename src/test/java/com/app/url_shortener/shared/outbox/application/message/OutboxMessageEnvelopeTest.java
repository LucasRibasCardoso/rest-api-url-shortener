package com.app.url_shortener.shared.outbox.application.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
@DisplayName("Testes de Unidade - Envelope de Mensagem Outbox")
class OutboxMessageEnvelopeTest {

  @Test
  @DisplayName("Deve manter metadados e payload do contrato de publicação")
  void shouldKeepPublicationContractMetadataAndPayload() {
    // 1. Arrange
    var eventId = UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0002");
    var occurredAt = Instant.parse("2026-06-11T20:00:00Z");
    var payload = new ObjectMapper().readTree("{\"userId\":\"user-id\"}");

    // 2. Act
    var envelope =
        new OutboxMessageEnvelope(
            eventId, "EMAIL_VERIFICATION_REQUESTED", 1, "USER", "user-id", occurredAt, payload);

    // 3. Assert
    assertAll(
        () -> assertThat(envelope.eventId()).isEqualTo(eventId),
        () -> assertThat(envelope.eventType()).isEqualTo("EMAIL_VERIFICATION_REQUESTED"),
        () -> assertThat(envelope.schemaVersion()).isEqualTo(1),
        () -> assertThat(envelope.aggregateType()).isEqualTo("USER"),
        () -> assertThat(envelope.aggregateId()).isEqualTo("user-id"),
        () -> assertThat(envelope.occurredAt()).isEqualTo(occurredAt),
        () -> assertThat(envelope.payload()).isEqualTo(payload));
  }
}
