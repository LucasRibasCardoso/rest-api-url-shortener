package com.app.url_shortener.shared.outbox.application.message;

import java.time.Instant;
import java.util.UUID;
import tools.jackson.databind.JsonNode;

public record OutboxMessageEnvelope(
    UUID eventId,
    String eventType,
    int schemaVersion,
    String aggregateType,
    String aggregateId,
    Instant occurredAt,
    JsonNode payload) {}
