package com.app.url_shortener.iam.infrastructure.notification.event;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.app.url_shortener.iam.application.event.EmailVerificationReason;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsAsyncOperations;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Listener de Evento de Verificação de Email")
class EmailVerificationEventListenerTest {

  private static final String QUEUE = "email-verification-events-queue";

  @Mock
  private SqsAsyncOperations sqsAsyncOperations;

  private EmailVerificationEventListener listener;

  @BeforeEach
  void setUp() {
    listener = new EmailVerificationEventListener(QUEUE, sqsAsyncOperations);
  }

  @Nested
  @DisplayName("Publicação após commit")
  class PublicationTests {

    @Test
    @DisplayName("Deve publicar evento na fila configurada")
    void shouldPublishEventToConfiguredQueue() {
      // 1. Arrange
      var event = event();
      var result = new CompletableFuture<SendResult<EmailVerificationRequestedEvent>>();
      when(sqsAsyncOperations.sendAsync(QUEUE, event)).thenReturn(result);

      // 2. Act
      listener.onEmailVerificationRequested(event);

      // 3. Assert
      verify(sqsAsyncOperations).sendAsync(QUEUE, event);
      verifyNoMoreInteractions(sqsAsyncOperations);
    }

    @Test
    @DisplayName("Não deve propagar falha imediata de publicação")
    void shouldNotPropagateImmediatePublicationFailure() {
      // 1. Arrange
      var event = event();
      when(sqsAsyncOperations.sendAsync(QUEUE, event))
          .thenThrow(new IllegalStateException("SQS unavailable"));

      // 2. Act & 3. Assert
      assertThatCode(() -> listener.onEmailVerificationRequested(event)).doesNotThrowAnyException();
      verify(sqsAsyncOperations).sendAsync(QUEUE, event);
    }

    @Test
    @DisplayName("Não deve propagar falha assíncrona de publicação")
    void shouldNotPropagateAsyncPublicationFailure() {
      // 1. Arrange
      var event = event();
      var result = new CompletableFuture<SendResult<EmailVerificationRequestedEvent>>();
      when(sqsAsyncOperations.sendAsync(QUEUE, event)).thenReturn(result);

      // 2. Act
      listener.onEmailVerificationRequested(event);

      // 3. Assert
      assertThatCode(() -> result.completeExceptionally(new IllegalStateException("SQS unavailable")))
          .doesNotThrowAnyException();
      verify(sqsAsyncOperations).sendAsync(QUEUE, event);
    }
  }

  private EmailVerificationRequestedEvent event() {
    return new EmailVerificationRequestedEvent(
        java.util.UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100"),
        java.util.UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101"),
        "user@email.com",
        EmailVerificationReason.REGISTER,
        Instant.parse("2026-06-09T12:00:00Z"));
  }
}
