package com.app.url_shortener.url.infrastructure.messaging.sqs;

import com.app.url_shortener.url.application.event.UrlRedirectedEvent;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;

@Slf4j
@Component
@RequiredArgsConstructor
public class UrlRedirectCounterConsumer {

  private final UrlRepositoryPort urlRepositoryPort;

  @SqsListener(
      value = "${app.aws.sqs.url-redirect-events-queue}",
      maxMessagesPerPoll = "10",
      acknowledgementMode = "MANUAL")
  public void consumeBatch(List<Message<UrlRedirectedEvent>> messages) {

    if (messages.isEmpty()) {
      return;
    }

    Map<String, List<Message<UrlRedirectedEvent>>> groupedMessages = groupMessages(messages);
    List<Message<UrlRedirectedEvent>> successfulMessages = new ArrayList<>();

    for (var entryMessage : groupedMessages.entrySet()) {

      var shortCode = entryMessage.getKey();
      var urlsGroup = entryMessage.getValue();
      long delta = urlsGroup.size();
      var lastAccessedAt = getLastAccessedAt(urlsGroup);

      try {
        urlRepositoryPort.incrementAccessCount(shortCode, delta, lastAccessedAt);
        successfulMessages.addAll(urlsGroup);
      } catch (DynamoDbException e) {
        log.error(
            "Falha ao incrementar acessos para o shortCode '{}'. As {} mensagens deste grupo sofrerão retry.",
            shortCode,
            delta,
            e);
      }
    }

    if (!successfulMessages.isEmpty()) {
      processSuccessMessages(successfulMessages);
    }
  }

  private Instant getLastAccessedAt(List<Message<UrlRedirectedEvent>> messages) {
    return messages.stream()
        .map(msg -> msg.getPayload().lastAccessedAt())
        .max(Instant::compareTo)
        .orElseThrow();
  }

  private void processSuccessMessages(List<Message<UrlRedirectedEvent>> messages) {
    Acknowledgement.acknowledge(messages);
    log.debug(
        "Processados {} eventos de redirecionamento para shortCodes: {}",
        messages.size(),
        messages.stream()
            .map(msg -> msg.getPayload().shortCode())
            .distinct()
            .collect(Collectors.joining(", ")));
  }

  private Map<String, List<Message<UrlRedirectedEvent>>> groupMessages(List<Message<UrlRedirectedEvent>> messages) {
    return messages.stream().collect(Collectors.groupingBy(msg -> msg.getPayload().shortCode()));
  }
}
