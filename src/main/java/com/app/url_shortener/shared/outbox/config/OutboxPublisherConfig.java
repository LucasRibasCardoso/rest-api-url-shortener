package com.app.url_shortener.shared.outbox.config;

import com.app.url_shortener.shared.outbox.application.policy.OutboxPublisherPolicy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxPublisherConfig {

  @Bean
  public OutboxPublisherPolicy outboxPublisherPolicy(OutboxPublisherProperties properties) {
    return new OutboxPublisherPolicy(properties.batchSize(), properties.maxAttempts(), properties.retryDelay());
  }
}
