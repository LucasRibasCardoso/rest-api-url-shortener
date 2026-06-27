package com.app.url_shortener.shared.outbox.infrastructure.resolver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.shared.outbox.config.OutboxSqsProperties;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventType;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Resolver de Fila Outbox")
class OutboxEventQueueResolverTest {

  private static final OutboxEventType EVENT_TYPE =
      OutboxEventType.of("EMAIL_VERIFICATION_REQUESTED");

  @Nested
  @DisplayName("Resolução")
  class ResolveTests {

    @Test
    @DisplayName("Deve resolver e normalizar fila configurada")
    void shouldResolveAndNormalizeConfiguredQueue() {
      // 1. Arrange
      var resolver = resolver(Map.of(EVENT_TYPE.value(), "  email-verification-events-queue  "));

      // 2. Act
      var result = resolver.resolve(EVENT_TYPE);

      // 3. Assert
      assertThat(result).isEqualTo("email-verification-events-queue");
    }

    @Test
    @DisplayName("Deve rejeitar tipo de evento nulo")
    void shouldRejectNullEventType() {
      // 1. Arrange
      var resolver = resolver(Map.of(EVENT_TYPE.value(), "email-verification-events-queue"));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> resolver.resolve(null));

      // 3. Assert
      throwableAssert
          .isInstanceOf(NullPointerException.class)
          .hasMessage("eventType must not be null");
    }

    @Test
    @DisplayName("Deve rejeitar fila ausente para o tipo de evento")
    void shouldRejectMissingQueueForEventType() {
      // 1. Arrange
      var resolver = resolver(Map.of());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> resolver.resolve(EVENT_TYPE));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "No SQS queue configured for outbox event type: EMAIL_VERIFICATION_REQUESTED");
    }

    @Test
    @DisplayName("Deve rejeitar configuração sem mapa de filas com erro descritivo")
    void shouldRejectConfigurationWithoutQueueMapWithDescriptiveError() {
      // 1. Arrange
      var resolver = resolver(null);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> resolver.resolve(EVENT_TYPE));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalStateException.class)
          .hasMessage(
              "No SQS queue configured for outbox event type: EMAIL_VERIFICATION_REQUESTED");
    }
  }

  private OutboxEventQueueResolver resolver(Map<String, String> queues) {
    return new OutboxEventQueueResolver(new OutboxSqsProperties(queues));
  }
}
