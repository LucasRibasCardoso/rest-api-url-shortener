package com.app.url_shortener.iam.domain.exception.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Exceção de Evento de Verificação em Processamento")
class EmailVerificationEventAlreadyProcessingExceptionTest {

  @Nested
  @DisplayName("Criação")
  class CreationTests {

    @Test
    @DisplayName("Deve adicionar eventId à mensagem")
    void shouldAddEventIdToMessage() {
      // 1. Arrange
      var eventId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100");

      // 2. Act
      var exception = new EmailVerificationEventAlreadyProcessingException(eventId);

      // 3. Assert
      assertThat(exception)
          .hasMessage("Evento de verificação de e-mail já em processamento. eventId=" + eventId);
    }

    @Test
    @DisplayName("Deve rejeitar eventId nulo")
    void shouldRejectNullEventId() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> new EmailVerificationEventAlreadyProcessingException(null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("eventId must not be null");
    }
  }
}
