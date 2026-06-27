package com.app.url_shortener.shared.outbox.infrastructure.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("Testes de Unidade - Value Object OutboxAggregateId")
class OutboxAggregateIdTest {

  @Nested
  @DisplayName("Criação e validação")
  class CreationAndValidationTests {

    @Test
    @DisplayName("Deve criar identificador removendo espaços externos")
    void shouldCreateAggregateIdTrimmingExternalSpaces() {
      // 1. Arrange
      var value = "  user-123  ";

      // 2. Act
      var aggregateId = OutboxAggregateId.of(value);

      // 3. Assert
      assertThat(aggregateId.value()).isEqualTo("user-123");
    }

    @Test
    @DisplayName("Deve aceitar identificador com tamanho máximo")
    void shouldAcceptAggregateIdWithMaximumLength() {
      // 1. Arrange
      var value = "a".repeat(160);

      // 2. Act
      var aggregateId = OutboxAggregateId.of(value);

      // 3. Assert
      assertThat(aggregateId.value()).hasSize(160);
    }

    @Test
    @DisplayName("Deve rejeitar identificador nulo")
    void shouldRejectNullAggregateId() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> OutboxAggregateId.of(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("aggregateId must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar identificador vazio ou em branco")
    void shouldRejectBlankAggregateId(String value) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> OutboxAggregateId.of(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("aggregateId must not be blank");
    }

    @Test
    @DisplayName("Deve rejeitar identificador acima do tamanho máximo")
    void shouldRejectAggregateIdAboveMaximumLength() {
      // 1. Arrange
      var value = "a".repeat(161);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> OutboxAggregateId.of(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("aggregateId must not exceed 160 characters");
    }
  }
}
