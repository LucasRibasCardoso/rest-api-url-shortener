package com.app.url_shortener.url.infrastructure.messaging.sqs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.url.application.event.UrlRedirectedEvent;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Consumidor SQS de Contador de Redirect")
@SuppressWarnings({"unchecked", "rawtypes"})
class UrlRedirectCounterConsumerTest {

  @Mock
  private UrlRepositoryPort urlRepositoryPort;

  @InjectMocks
  private UrlRedirectCounterConsumer consumer;

  @Nested
  @DisplayName("Consumo em lote")
  class ConsumeBatchTests {

    @Test
    @DisplayName("Deve ignorar lote vazio")
    void shouldIgnoreEmptyBatch() {
      // 1. Arrange
      try (MockedStatic<Acknowledgement> acknowledgement = mockStatic(Acknowledgement.class)) {

        // 2. Act
        consumer.consumeBatch(List.of());

        // 3. Assert
        verifyNoInteractions(urlRepositoryPort);
        acknowledgement.verifyNoInteractions();
      }
    }

    @Test
    @DisplayName("Deve agrupar mensagens pelo código curto e incrementar com maior timestamp")
    void shouldGroupMessagesByShortCodeAndIncrementUsingLatestTimestamp() {
      // 1. Arrange
      var firstAccessedAt = Instant.parse("2026-06-06T12:30:45Z");
      var latestAccessedAt = Instant.parse("2026-06-06T12:35:45Z");
      var messages =
          List.of(
              message("aB3dE", firstAccessedAt),
              message("aB3dE", latestAccessedAt),
              message("aB3dE", Instant.parse("2026-06-06T12:32:45Z")));

      try (MockedStatic<Acknowledgement> acknowledgement = mockStatic(Acknowledgement.class)) {

        // 2. Act
        consumer.consumeBatch(messages);

        // 3. Assert
        verify(urlRepositoryPort).incrementAccessCount("aB3dE", 3L, latestAccessedAt);
        verifyNoMoreInteractions(urlRepositoryPort);
        acknowledgement.verify(() -> Acknowledgement.acknowledge(messages));
        acknowledgement.verifyNoMoreInteractions();
      }
    }

    @Test
    @DisplayName("Deve incrementar cada grupo de código curto e confirmar mensagens processadas")
    void shouldIncrementEachShortCodeGroupAndAcknowledgeProcessedMessages() {
      // 1. Arrange
      var firstCodeLatestAccessedAt = Instant.parse("2026-06-06T12:35:45Z");
      var secondCodeLatestAccessedAt = Instant.parse("2026-06-06T12:40:45Z");
      var messages =
          List.of(
              message("aB3dE", Instant.parse("2026-06-06T12:30:45Z")),
              message("fG4hI", secondCodeLatestAccessedAt),
              message("aB3dE", firstCodeLatestAccessedAt));

      try (MockedStatic<Acknowledgement> acknowledgement = mockStatic(Acknowledgement.class)) {

        // 2. Act
        consumer.consumeBatch(messages);

        // 3. Assert
        verify(urlRepositoryPort).incrementAccessCount("aB3dE", 2L, firstCodeLatestAccessedAt);
        verify(urlRepositoryPort).incrementAccessCount("fG4hI", 1L, secondCodeLatestAccessedAt);
        verifyNoMoreInteractions(urlRepositoryPort);

        var messagesCaptor = acknowledgeMessagesCaptor();
        acknowledgement.verify(() -> Acknowledgement.acknowledge(messagesCaptor.capture()));
        assertThat(messagesCaptor.getValue()).containsExactlyInAnyOrderElementsOf(messages);
        acknowledgement.verifyNoMoreInteractions();
      }
    }

    @Test
    @DisplayName("Deve confirmar somente grupos processados com sucesso quando outro grupo falhar")
    void shouldAcknowledgeOnlySuccessfulGroupsWhenAnotherGroupFails() {
      // 1. Arrange
      var successfulMessage = message("aB3dE", Instant.parse("2026-06-06T12:30:45Z"));
      var failedMessage = message("fG4hI", Instant.parse("2026-06-06T12:40:45Z"));
      var messages = List.of(successfulMessage, failedMessage);
      var failure = DynamoDbException.builder().message("DynamoDB unavailable").build();
      doAnswer(
              invocation -> {
                if ("fG4hI".equals(invocation.getArgument(0))) {
                  throw failure;
                }

                return null;
              })
          .when(urlRepositoryPort)
          .incrementAccessCount(any(), anyLong(), any());

      try (MockedStatic<Acknowledgement> acknowledgement = mockStatic(Acknowledgement.class)) {

        // 2. Act & 3. Assert
        assertThatCode(() -> consumer.consumeBatch(messages)).doesNotThrowAnyException();
        verify(urlRepositoryPort)
            .incrementAccessCount("aB3dE", 1L, successfulMessage.getPayload().lastAccessedAt());
        verify(urlRepositoryPort)
            .incrementAccessCount("fG4hI", 1L, failedMessage.getPayload().lastAccessedAt());
        verifyNoMoreInteractions(urlRepositoryPort);

        var messagesCaptor = acknowledgeMessagesCaptor();
        acknowledgement.verify(() -> Acknowledgement.acknowledge(messagesCaptor.capture()));
        assertThat(messagesCaptor.getValue()).containsExactly(successfulMessage);
        acknowledgement.verifyNoMoreInteractions();
      }
    }

    @Test
    @DisplayName("Não deve confirmar mensagens quando todos os grupos falharem")
    void shouldNotAcknowledgeMessagesWhenAllGroupsFail() {
      // 1. Arrange
      var lastAccessedAt = Instant.parse("2026-06-06T12:30:45Z");
      var messages = List.of(message("aB3dE", lastAccessedAt));
      var failure = DynamoDbException.builder().message("DynamoDB unavailable").build();
      doThrow(failure)
          .when(urlRepositoryPort)
          .incrementAccessCount("aB3dE", 1L, lastAccessedAt);

      try (MockedStatic<Acknowledgement> acknowledgement = mockStatic(Acknowledgement.class)) {

        // 2. Act & 3. Assert
        assertThatCode(() -> consumer.consumeBatch(messages)).doesNotThrowAnyException();
        verify(urlRepositoryPort).incrementAccessCount("aB3dE", 1L, lastAccessedAt);
        verifyNoMoreInteractions(urlRepositoryPort);
        acknowledgement.verifyNoInteractions();
      }
    }
  }

  private Message<UrlRedirectedEvent> message(String shortCode, Instant lastAccessedAt) {
    return MessageBuilder.withPayload(
            new UrlRedirectedEvent(
                UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100"),
                shortCode,
                lastAccessedAt))
        .build();
  }

  private ArgumentCaptor<Collection<Message<UrlRedirectedEvent>>> acknowledgeMessagesCaptor() {
    return ArgumentCaptor.forClass(Collection.class);
  }
}
