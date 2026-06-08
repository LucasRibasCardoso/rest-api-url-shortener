package com.app.url_shortener.url.infrastructure.messaging.sqs;

import com.app.url_shortener.url.application.event.UrlRedirectedEvent;
import com.app.url_shortener.url.application.port.output.UrlRedirectEventPublisherPort;
import io.awspring.cloud.sqs.operations.SqsAsyncOperations;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UrlRedirectEventPublisherAdapter implements UrlRedirectEventPublisherPort {

  private final SqsAsyncOperations sqsAsyncOperations;
  private final String urlRedirectEventsQueue;

  public UrlRedirectEventPublisherAdapter(
      @Value("${app.aws.sqs.url-redirect-events-queue}") String urlRedirectEventsQueue,
      SqsAsyncOperations sqsAsyncOperations) {
    this.urlRedirectEventsQueue = urlRedirectEventsQueue;
    this.sqsAsyncOperations = sqsAsyncOperations;
  }

  @Override
  public void publishAsync(UrlRedirectedEvent event) {
    try {
      sqsAsyncOperations
          .sendAsync(urlRedirectEventsQueue, event)
          .whenComplete(
              (result, exception) -> {
                if (exception != null) {
                  log.warn(
                      "Falha ao publicar evento de redirecionamento de URL. eventId={}, shortCode={}, queue={}",
                      event.eventId(),
                      event.shortCode(),
                      urlRedirectEventsQueue,
                      exception);
                  return;
                }

                log.debug(
                    "Evento de redirecionamento de URL publicado com sucesso. eventId={}, shortCode={}, queue={}, messageId={}",
                    event.eventId(),
                    event.shortCode(),
                    urlRedirectEventsQueue,
                    result.messageId());
              });
    } catch (RuntimeException exception) {
      log.warn(
          "Falha imediata ao iniciar publicação assíncrona de evento de redirecionamento de URL. eventId={}, shortCode={}, queue={}",
          event.eventId(),
          event.shortCode(),
          urlRedirectEventsQueue,
          exception);
    }
  }
}
