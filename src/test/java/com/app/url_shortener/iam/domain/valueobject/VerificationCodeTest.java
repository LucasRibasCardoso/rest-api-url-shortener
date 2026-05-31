package com.app.url_shortener.iam.domain.valueobject;

import com.app.url_shortener.iam.domain.exception.user.InvalidVerificationCodeException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@DisplayName("Testes de Unidade - Value Object VerificationCode")
class VerificationCodeTest {

  @Nested
  @DisplayName("Validação de Formato e Criação")
  class ValidationTests {

    @ParameterizedTest
    @ValueSource(strings = {"123456", "000000", "999999"})
    @DisplayName("Deve criar o código com sucesso quando o valor tiver exatamente 6 dígitos numéricos")
    void shouldCreateCodeWhenValueIsValid(String validValue) {
      // Arrange & Act
      var code = VerificationCode.of(validValue);

      // Assert
      assertThat(code.value()).isEqualTo(validValue);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "12345",
            "1234567",
            "abcdef",
            "12 456",
            "12-456"
    })
    @DisplayName("Deve lançar exceção quando o valor não for composto por exatamente 6 dígitos numéricos")
    void shouldThrowExceptionWhenValueIsInvalid(String invalidValue) {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> VerificationCode.of(invalidValue))
              .isInstanceOf(InvalidVerificationCodeException.class);
    }

    @Test
    @DisplayName("Deve lançar exceção quando o valor for nulo")
    void shouldThrowExceptionWhenValueIsNull() {
      // Arrange & Act & Assert
      assertThatThrownBy(() -> VerificationCode.of(null))
              .isInstanceOf(InvalidVerificationCodeException.class);
    }
  }

  @Nested
  @DisplayName("Geração de Código Aleatório")
  class GenerationTests {

    @Test
    @DisplayName("Deve gerar um código aleatório válido com 6 dígitos, completando com zeros à esquerda se necessário")
    void shouldGenerateValidRandomCode() {
      // Arrange & Act
      var code = VerificationCode.generate();

      // Assert
      assertThat(code).isNotNull();
      assertThat(code.value()).matches("\\d{6}");
    }
  }

  @Nested
  @DisplayName("Representação textual segura")
  class SafeToStringTests {

    @Test
    @DisplayName("Não deve expor o código de verificação no toString")
    void shouldNotExposeVerificationCodeInToString() {
      // 1. Arrange
      var rawCode = "123456";
      var code = VerificationCode.of(rawCode);

      // 2. Act
      var text = code.toString();

      // 3. Assert
      assertThat(text)
          .doesNotContain(rawCode)
          .contains("[REDACTED]");
    }
  }
}
