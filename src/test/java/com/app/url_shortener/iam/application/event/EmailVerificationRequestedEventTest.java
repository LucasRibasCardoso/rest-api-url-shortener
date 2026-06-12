package com.app.url_shortener.iam.application.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

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

    @Test
    @DisplayName("Deve projetar somente os dados necessários para verificação")
    void shouldProjectOnlyRequiredVerificationData() throws JacksonException {
      // 1. Arrange
      var event = event();
      var objectMapper = new ObjectMapper();

      // 2. Act
      var payload = event.toPayload();
      var payloadJson = objectMapper.writeValueAsString(payload);

      // 3. Assert
      assertAll(
          () -> assertThat(payload.userId()).isEqualTo(event.userId()),
          () -> assertThat(payload.email()).isEqualTo(event.email()),
          () -> assertThat(payload.reason()).isEqualTo(event.reason()),
          () ->
              assertThat(payload.getClass().getRecordComponents())
                  .extracting(component -> component.getName())
                  .containsExactly("userId", "email", "reason"),
          () ->
              assertThat(payloadJson)
                  .isEqualTo(
                      """
                      {"userId":"019a1a4f-d0db-7f94-bd8d-63d831f40001","email":"user@email.com","reason":"REGISTER"}\
                      """));
    }
  }

  @Nested
  @DisplayName("Validação")
  class ValidationTests {

    @Test
    @DisplayName("Deve rejeitar email nulo")
    void shouldRejectNullEmail() {
      // 1. Arrange

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  new EmailVerificationRequestedEvent(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      null,
                      EmailVerificationReason.REGISTER,
                      Instant.now()));

      // 3. Assert
      throwableAssert.isInstanceOf(NullPointerException.class).hasMessage("email is required");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar email vazio ou em branco")
    void shouldRejectBlankEmail(String blankEmail) {
      // 1. Arrange

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(
              () ->
                  new EmailVerificationRequestedEvent(
                      UUID.randomUUID(),
                      UUID.randomUUID(),
                      blankEmail,
                      EmailVerificationReason.REGISTER,
                      Instant.now()));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("email must not be blank");
    }
  }

  private EmailVerificationRequestedEvent event() {
    return new EmailVerificationRequestedEvent(
        UUID.fromString("019a1a4f-d0db-7f94-bd8d-63d831f40002"),
        UUID.fromString("019a1a4f-d0db-7f94-bd8d-63d831f40001"),
        "user@email.com",
        EmailVerificationReason.REGISTER,
        Instant.parse("2026-06-10T20:00:00Z"));
  }
}
