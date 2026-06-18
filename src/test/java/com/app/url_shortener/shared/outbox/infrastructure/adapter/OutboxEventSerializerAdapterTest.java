package com.app.url_shortener.shared.outbox.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.shared.outbox.domain.exception.OutboxEventSerializationException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Serializador de Eventos Outbox")
class OutboxEventSerializerAdapterTest {

  @Mock
  private ObjectMapper objectMapper;

  @InjectMocks
  private OutboxEventSerializerAdapter adapter;

  @Nested
  @DisplayName("Serialização")
  class SerializationTests {

    @Test
    @DisplayName("Deve retornar JSON produzido pelo ObjectMapper")
    void shouldReturnJsonProducedByObjectMapper() throws JacksonException {
      // 1. Arrange
      var event = event();
      var json = "{\"eventId\":\"" + event.eventId() + "\"}";
      given(objectMapper.writeValueAsString(event)).willReturn(json);

      // 2. Act
      var result = adapter.serialize(event);

      // 3. Assert
      assertThat(result).isEqualTo(json);
      verify(objectMapper).writeValueAsString(event);
      verifyNoMoreInteractions(objectMapper);
    }

    @Test
    @DisplayName("Deve traduzir falha do Jackson para exceção do Outbox")
    void shouldTranslateJacksonFailureToOutboxException() throws JacksonException {
      // 1. Arrange
      var event = event();
      var jacksonException = mock(JacksonException.class);
      given(objectMapper.writeValueAsString(event)).willThrow(jacksonException);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.serialize(event));

      // 3. Assert
      throwableAssert.isInstanceOf(OutboxEventSerializationException.class);
      verify(objectMapper).writeValueAsString(event);
      verifyNoMoreInteractions(objectMapper);
    }
  }

  private static EmailVerificationRequestedEvent event() {
    return new EmailVerificationRequestedEvent(
        UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0002"),
        UUID.fromString("019a1a60-8e31-73b0-bc44-238e6aea0003"),
        "user@email.com",
        EmailDispatchReason.REGISTER,
        Instant.parse("2026-06-10T20:00:00Z"));
  }
}
