package com.app.url_shortener.shared.outbox.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - Configuração do publisher outbox")
class OutboxPublisherConfigTest {

  @Test
  @DisplayName("Deve converter properties para policy Spring-free")
  void shouldConvertPropertiesToSpringFreePolicy() {
    // 1. Arrange
    var properties =
        new OutboxPublisherProperties(
            true, 20, Duration.ofSeconds(5), Duration.ofSeconds(5), 5, Duration.ofSeconds(30));
    var config = new OutboxPublisherConfig();

    // 2. Act
    var policy = config.outboxPublisherPolicy(properties);

    // 3. Assert
    assertThat(policy.batchSize()).isEqualTo(20);
    assertThat(policy.maxAttempts()).isEqualTo(5);
    assertThat(policy.retryDelay()).isEqualTo(Duration.ofSeconds(30));
  }
}
