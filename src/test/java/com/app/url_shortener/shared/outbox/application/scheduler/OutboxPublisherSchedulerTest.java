package com.app.url_shortener.shared.outbox.application.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.shared.outbox.application.service.OutboxPublisherService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Scheduler Publisher Outbox")
class OutboxPublisherSchedulerTest {

  @Mock
  private OutboxPublisherService outboxPublisherService;

  @InjectMocks
  private OutboxPublisherScheduler scheduler;

  @Nested
  @DisplayName("Publicação agendada")
  class PublishPendingEventsTests {

    @Test
    @DisplayName("Deve delegar publicação de eventos pendentes ao serviço")
    void shouldDelegatePendingEventPublicationToService() {
      // 1. Arrange

      // 2. Act
      scheduler.publishPendingEvents();

      // 3. Assert
      verify(outboxPublisherService).publishPendingEvents();
      verifyNoMoreInteractions(outboxPublisherService);
    }
  }
}
