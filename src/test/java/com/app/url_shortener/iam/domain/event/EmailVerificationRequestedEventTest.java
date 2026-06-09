package com.app.url_shortener.iam.domain.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Evento EmailVerificationEvent")
class EmailVerificationRequestedEventTest {

  @Nested
  @DisplayName("Criação")
  class CreationTests {

    @Test
    @DisplayName("Deve criar evento com identificador, razão e data")
    void shouldCreateEventWithIdentifierReasonAndOccurredAt() {
      // 1. Arrange
      var userId = UUID.randomUUID();

      // 2. Act
      var event =
          EmailVerificationRequestedEvent.create(
              userId, "  USUARIO@EMAIL.COM ", EmailVerificationReason.REGISTER);

      // 3. Assert
      assertThat(event.eventId()).isNotNull();
      assertThat(event.userId()).isEqualTo(userId);
      assertThat(event.email()).isEqualTo("usuario@email.com");
      assertThat(event.reason()).isEqualTo(EmailVerificationReason.REGISTER);
      assertThat(event.occurredAt()).isNotNull();
    }
  }
}
