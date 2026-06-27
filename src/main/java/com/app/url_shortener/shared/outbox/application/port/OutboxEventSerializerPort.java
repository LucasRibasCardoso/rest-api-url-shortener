package com.app.url_shortener.shared.outbox.application.port;

public interface OutboxEventSerializerPort {

  String serialize(Object event);
}
