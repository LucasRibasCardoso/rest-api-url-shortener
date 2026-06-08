package com.app.url_shortener.iam.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Evento EmailVerificationEvent")
class EmailVerificationRequestedEventTest {

  @Nested
  @DisplayName("Representação textual segura")
  class SafeToStringTests {

    @Test
    @DisplayName("Não deve expor o código de verificação no toString")
    void shouldNotExposeVerificationCodeInToString() {
      // 1. Arrange
      var rawCode = "123456";
      var event =
          EmailVerificationRequestedEvent.create(
              UUID.randomUUID(), "usuario@email.com", VerificationCode.of(rawCode));

      // 2. Act
      var text = event.toString();

      // 3. Assert
      assertThat(text).doesNotContain(rawCode).contains("[REDACTED]");
    }
  }
}
