package com.app.url_shortener.shared.domain.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("Testes de Unidade - Validação de texto obrigatório")
class RequiredTextTest {

  @Nested
  @DisplayName("Normalização")
  class NormalizationTests {

    @Test
    @DisplayName("Deve remover espaços externos de um texto válido")
    void shouldTrimValidText() {
      // 1. Arrange
      var value = "  valid text  ";

      // 2. Act
      var normalizedValue = RequiredText.normalize(value, "field");

      // 3. Assert
      assertThat(normalizedValue).isEqualTo("valid text");
    }

    @Test
    @DisplayName("Deve rejeitar texto nulo")
    void shouldRejectNullText() {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> RequiredText.normalize(null, "field"));

      // 3. Assert
      throwableAssert.isInstanceOf(NullPointerException.class).hasMessage("field is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar texto vazio ou em branco")
    void shouldRejectBlankText(String blankValue) {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> RequiredText.normalize(blankValue, "field"));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("field must not be blank");
    }
  }
}
