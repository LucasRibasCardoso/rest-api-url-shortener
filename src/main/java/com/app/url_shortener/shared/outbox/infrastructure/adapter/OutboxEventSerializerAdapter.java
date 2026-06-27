package com.app.url_shortener.shared.outbox.infrastructure.adapter;

import com.app.url_shortener.shared.outbox.application.port.OutboxEventSerializerPort;
import com.app.url_shortener.shared.outbox.domain.exception.OutboxEventSerializationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
@RequiredArgsConstructor
public class OutboxEventSerializerAdapter implements OutboxEventSerializerPort {

  private final ObjectMapper objectMapper;

  @Override
  public String serialize(Object event) {
    try {
      return objectMapper.writeValueAsString(event);
    } catch (JacksonException e) {
      throw new OutboxEventSerializationException(e);
    }
  }
}
