package com.app.url_shortener.url.infrastructure.messaging.sqs;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.app.url_shortener.url.application.event.UrlRedirectedEvent;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsAsyncOperations;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.support.MessageBuilder;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador Publicador de Eventos de Redirect")
class UrlRedirectEventPublisherAdapterTest {

  private static final String URL_REDIRECT_EVENTS_QUEUE = "url-redirect-events";

  @Mock
  private SqsAsyncOperations sqsAsyncOperations;

  private UrlRedirectEventPublisherAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new UrlRedirectEventPublisherAdapter(URL_REDIRECT_EVENTS_QUEUE, sqsAsyncOperations);
  }

  @Nested
  @DisplayName("Publicação assíncrona")
  class PublishAsyncTests {

    @Test
    @DisplayName("Deve enviar evento para fila configurada")
    void shouldSendEventToConfiguredQueue() {
      // 1. Arrange
      var event = event();
      var resultFuture = new CompletableFuture<SendResult<UrlRedirectedEvent>>();
      when(sqsAsyncOperations.sendAsync(URL_REDIRECT_EVENTS_QUEUE, event)).thenReturn(resultFuture);

      // 2. Act
      adapter.publish(event);

      // 3. Assert
      verify(sqsAsyncOperations).sendAsync(URL_REDIRECT_EVENTS_QUEUE, event);
      verifyNoMoreInteractions(sqsAsyncOperations);
    }

    @Test
    @DisplayName("Deve ignorar falha imediata ao iniciar publicação assíncrona")
    void shouldIgnoreImmediateFailureWhenStartingAsyncPublication() {
      // 1. Arrange
      var event = event();
      var exception = new IllegalStateException("SQS unavailable");
      when(sqsAsyncOperations.sendAsync(URL_REDIRECT_EVENTS_QUEUE, event)).thenThrow(exception);

      // 2. Act & 3. Assert
      assertThatCode(() -> adapter.publish(event)).doesNotThrowAnyException();
      verify(sqsAsyncOperations).sendAsync(URL_REDIRECT_EVENTS_QUEUE, event);
      verifyNoMoreInteractions(sqsAsyncOperations);
    }

    @Test
    @DisplayName("Deve ignorar falha ao completar publicação assíncrona")
    void shouldIgnoreFailureWhenAsyncPublicationCompletesExceptionally() {
      // 1. Arrange
      var event = event();
      var resultFuture = new CompletableFuture<SendResult<UrlRedirectedEvent>>();
      var exception = new IllegalStateException("SQS unavailable");
      when(sqsAsyncOperations.sendAsync(URL_REDIRECT_EVENTS_QUEUE, event)).thenReturn(resultFuture);

      // 2. Act
      adapter.publish(event);

      // 3. Assert
      assertThatCode(() -> resultFuture.completeExceptionally(exception)).doesNotThrowAnyException();
      verify(sqsAsyncOperations).sendAsync(URL_REDIRECT_EVENTS_QUEUE, event);
      verifyNoMoreInteractions(sqsAsyncOperations);
    }

    @Test
    @DisplayName("Deve finalizar sem exceção quando publicação assíncrona for concluída com sucesso")
    void shouldCompleteWithoutExceptionWhenAsyncPublicationSucceeds() {
      // 1. Arrange
      var event = event();
      var resultFuture = new CompletableFuture<SendResult<UrlRedirectedEvent>>();
      var sendResult =
          new SendResult<>(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101"),
              URL_REDIRECT_EVENTS_QUEUE,
              MessageBuilder.withPayload(event).build(),
              Map.of());
      when(sqsAsyncOperations.sendAsync(URL_REDIRECT_EVENTS_QUEUE, event)).thenReturn(resultFuture);

      // 2. Act
      adapter.publish(event);

      // 3. Assert
      assertThatCode(() -> resultFuture.complete(sendResult)).doesNotThrowAnyException();
      verify(sqsAsyncOperations).sendAsync(URL_REDIRECT_EVENTS_QUEUE, event);
      verifyNoMoreInteractions(sqsAsyncOperations);
    }
  }

  private UrlRedirectedEvent event() {
    return new UrlRedirectedEvent(
        UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100"),
        "aB3dE",
        Instant.parse("2026-06-06T12:30:45Z"));
  }
}
