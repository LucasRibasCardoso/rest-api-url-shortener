package com.app.url_shortener.shared.outbox.infrastructure.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.shared.outbox.domain.model.OutboxAggregateType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("Testes de Unidade - Value Object OutboxAggregateType")
class OutboxAggregateTypeTest {

  @Nested
  @DisplayName("Criação e validação")
  class CreationAndValidationTests {

    @ParameterizedTest
    @ValueSource(strings = {"USER", "USER_ACCOUNT", "USER2"})
    @DisplayName("Deve criar tipo de agregado em formato válido")
    void shouldCreateValidAggregateType(String value) {
      // 1. Arrange

      // 2. Act
      var aggregateType = OutboxAggregateType.of(value);

      // 3. Assert
      assertThat(aggregateType.value()).isEqualTo(value);
    }

    @Test
    @DisplayName("Deve remover espaços externos")
    void shouldTrimExternalSpaces() {
      // 1. Arrange

      // 2. Act
      var aggregateType = OutboxAggregateType.of("  USER  ");

      // 3. Assert
      assertThat(aggregateType.value()).isEqualTo("USER");
    }

    @Test
    @DisplayName("Deve aceitar tipo com tamanho máximo")
    void shouldAcceptAggregateTypeWithMaximumLength() {
      // 1. Arrange
      var value = "A".repeat(80);

      // 2. Act
      var aggregateType = OutboxAggregateType.of(value);

      // 3. Assert
      assertThat(aggregateType.value()).hasSize(80);
    }

    @Test
    @DisplayName("Deve rejeitar tipo nulo")
    void shouldRejectNullAggregateType() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> OutboxAggregateType.of(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("aggregateType must not be null");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar tipo vazio ou em branco")
    void shouldRejectBlankAggregateType(String value) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> OutboxAggregateType.of(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("aggregateType must not be blank");
    }

    @ParameterizedTest
    @ValueSource(strings = {"user", "1USER", "_USER", "USER-ACCOUNT", "USER ACCOUNT"})
    @DisplayName("Deve rejeitar tipo fora do formato permitido")
    void shouldRejectInvalidAggregateTypeFormat(String value) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> OutboxAggregateType.of(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("aggregateType must use format A-Z, 0-9 and underscore");
    }

    @Test
    @DisplayName("Deve rejeitar tipo acima do tamanho máximo")
    void shouldRejectAggregateTypeAboveMaximumLength() {
      // 1. Arrange
      var value = "A".repeat(81);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> OutboxAggregateType.of(value))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("aggregateType must not exceed 80 characters");
    }
  }
}
