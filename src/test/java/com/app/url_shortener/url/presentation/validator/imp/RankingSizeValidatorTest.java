package com.app.url_shortener.url.presentation.validator.imp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@DisplayName("Testes de Unidade - Validador de Tamanho de Ranking")
class RankingSizeValidatorTest {

  private RankingSizeValidator validator;

  @BeforeEach
  void setUp() {
    validator = new RankingSizeValidator();
  }

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @ParameterizedTest
    @NullSource
    @ValueSource(ints = {3, 10})
    @DisplayName("Deve retornar verdadeiro para tamanhos de ranking permitidos")
    void shouldReturnTrueForAllowedRankingSizes(Integer value) {
      // 1. Arrange

      // 2. Act
      var result = validator.isValid(value, null);

      // 3. Assert
      assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 4, 5, 9, 11})
    @DisplayName("Deve retornar falso para tamanhos de ranking não permitidos")
    void shouldReturnFalseForDisallowedRankingSizes(Integer value) {
      // 1. Arrange

      // 2. Act
      var result = validator.isValid(value, null);

      // 3. Assert
      assertThat(result).isFalse();
    }
  }
}
