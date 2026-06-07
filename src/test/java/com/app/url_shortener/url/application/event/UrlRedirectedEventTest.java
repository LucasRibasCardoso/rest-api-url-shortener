package com.app.url_shortener.url.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("Testes de Unidade - Evento UrlRedirectedEvent")
class UrlRedirectedEventTest {

  private static final UUID EVENT_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");
  private static final Instant LAST_ACCESSED_AT = Instant.parse("2026-06-06T12:30:45Z");

  @Nested
  @DisplayName("Criação")
  class CreationTests {

    @Test
    @DisplayName("Deve criar evento com identificador, código curto normalizado e data do acesso")
    void shouldCreateEventWithIdNormalizedShortCodeAndAccessTime() {
      // 1. Arrange
      var beforeCreation = Instant.now();

      // 2. Act
      var event = UrlRedirectedEvent.create("  aB3dE  ");
      var afterCreation = Instant.now();

      // 3. Assert
      assertThat(event.eventId()).isNotNull();
      assertThat(event.shortCode()).isEqualTo("aB3dE");
      assertThat(event.lastAccessedAt()).isBetween(beforeCreation, afterCreation);
    }
  }

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @Test
    @DisplayName("Deve rejeitar identificador de evento nulo")
    void shouldRejectNullEventId() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> new UrlRedirectedEvent(null, "aB3dE", LAST_ACCESSED_AT))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("eventId must not be null");
    }

    @Test
    @DisplayName("Deve rejeitar data do acesso nula")
    void shouldRejectNullLastAccessedAt() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> new UrlRedirectedEvent(EVENT_ID, "aB3dE", null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("lastAccessedAt must not be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar código curto ausente ou em branco")
    void shouldRejectMissingOrBlankShortCode(String shortCode) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> new UrlRedirectedEvent(EVENT_ID, shortCode, LAST_ACCESSED_AT))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("shortCode must not be blank");
    }
  }
}
