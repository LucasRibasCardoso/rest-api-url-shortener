package com.app.url_shortener.iam.infrastructure.notification.event;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador Publicador de Evento de Verificação de Email")
class EmailVerificationEventPublisherAdapterTest {

  @Mock
  private ApplicationEventPublisher applicationEventPublisher;

  @InjectMocks
  private EmailVerificationEventPublisherAdapter adapter;

  @Nested
  @DisplayName("Publicação")
  class PublishTests {

    @Test
    @DisplayName("Deve delegar publicação do evento ao Spring")
    void shouldDelegateEventPublicationToSpring() {
      // 1. Arrange
      var event =
          new EmailVerificationRequestedEvent(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100"),
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101"),
              "user@email.com",
              VerificationCode.of("123456"));

      // 2. Act
      adapter.publish(event);

      // 3. Assert
      verify(applicationEventPublisher).publishEvent(event);
      verifyNoMoreInteractions(applicationEventPublisher);
    }
  }
}
