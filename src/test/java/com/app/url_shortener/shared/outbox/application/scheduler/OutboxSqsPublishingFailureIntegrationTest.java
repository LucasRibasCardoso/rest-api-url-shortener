package com.app.url_shortener.shared.outbox.application.scheduler;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.infrastructure.repository.EmailDispatchJpaRepository;
import com.app.url_shortener.iam.infrastructure.repository.EmailVerificationTokenJpaRepository;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import com.app.url_shortener.shared.outbox.infrastructure.entity.OutboxEventEntity;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.restassured.http.ContentType;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "spring.cloud.aws.sqs.listener.auto-startup=false",
      "app.outbox.publisher.enabled=true",
      "app.outbox.publisher.max-attempts=2",
      "app.outbox.publisher.retry-delay=10ms",
      "app.outbox.publisher.fixed-delay=1h",
      "app.outbox.publisher.initial-delay=1h",
      "app.outbox.sqs.queues.EMAIL_VERIFICATION_REQUESTED=missing-email-verification-events-queue",
      "app.outbox.sqs.queues.PARTIAL_SUCCESS_EVENT=email-verification-events-queue"
    })
@DisplayName("Testes de Integração - Falha na publicação da outbox na SQS")
class OutboxSqsPublishingFailureIntegrationTest extends AbstractIntegrationTest {

  private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
  private static final String QUEUE_NAME = "email-verification-events-queue";

  private final SqsTemplate sqsTemplate;
  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final EmailDispatchJpaRepository emailDispatchJpaRepository;
  private final EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository;
  private final OutboxPublisherScheduler outboxPublisherScheduler;

  @Autowired
  OutboxSqsPublishingFailureIntegrationTest(
      SqsTemplate sqsTemplate,
      OutboxEventJpaRepository outboxEventJpaRepository,
      EmailDispatchJpaRepository emailDispatchJpaRepository,
      EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository,
      OutboxPublisherScheduler outboxPublisherScheduler) {
    this.sqsTemplate = sqsTemplate;
    this.outboxEventJpaRepository = outboxEventJpaRepository;
    this.emailDispatchJpaRepository = emailDispatchJpaRepository;
    this.emailVerificationTokenJpaRepository = emailVerificationTokenJpaRepository;
    this.outboxPublisherScheduler = outboxPublisherScheduler;
  }

  @Test
  @DisplayName("Deve registrar retry e marcar evento como falho ao esgotar tentativas")
  void shouldRetryAndFailEventWhenSqsQueueDoesNotExist() {
    // Arrange
    register();
    OutboxEventEntity initialEvent = outboxEventJpaRepository.findAll().getFirst();

    // Act
    outboxPublisherScheduler.publishPendingEvents();
    OutboxEventEntity retriedEvent =
        outboxEventJpaRepository.findById(initialEvent.getId()).orElseThrow();

    await()
        .atMost(Duration.ofSeconds(2))
        .until(() -> !retriedEvent.getNextAttemptAt().isAfter(Instant.now()));
    outboxPublisherScheduler.publishPendingEvents();

    // Assert
    OutboxEventEntity failedEvent =
        outboxEventJpaRepository.findById(initialEvent.getId()).orElseThrow();

    assertThat(retriedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(retriedEvent.getAttempts()).isEqualTo(1);
    assertThat(retriedEvent.getLastError())
        .isEqualTo(CommonErrorCode.OUTBOX_EVENT_PUBLISH_ERROR_EXCEPTION.getMessage());
    assertThat(retriedEvent.getNextAttemptAt()).isNotNull();
    assertThat(retriedEvent.getPublishedAt()).isNull();
    assertThat(failedEvent.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    assertThat(failedEvent.getAttempts()).isEqualTo(2);
    assertThat(failedEvent.getLastError())
        .isEqualTo(CommonErrorCode.OUTBOX_EVENT_PUBLISH_ERROR_EXCEPTION.getMessage());
    assertThat(failedEvent.getNextAttemptAt()).isNull();
    assertThat(failedEvent.getPublishedAt()).isNull();
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName("Deve continuar publicando lote quando um evento falhar")
  void shouldContinuePublishingBatchWhenOneEventFails() {
    // Arrange
    var now = Instant.now();
    var failedEventId = UUID.fromString("019b1ec0-4f2a-7d90-9c20-111111111111");
    var publishedEventId = UUID.fromString("019b1ec0-4f2a-7d90-9c20-222222222222");

    outboxEventJpaRepository.saveAll(
        List.of(
            outboxEvent(
                failedEventId,
                IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED,
                now.minusSeconds(20)),
            outboxEvent(publishedEventId, "PARTIAL_SUCCESS_EVENT", now.minusSeconds(10))));

    // Act
    outboxPublisherScheduler.publishPendingEvents();
    Collection<Message<OutboxMessageEnvelope>> messages =
        sqsTemplate.receiveMany(
            options ->
                options
                    .queue(QUEUE_NAME)
                    .maxNumberOfMessages(10)
                    .pollTimeout(Duration.ofSeconds(2)),
            OutboxMessageEnvelope.class);

    // Assert
    var failedEvent = outboxEventJpaRepository.findById(failedEventId).orElseThrow();
    var publishedEvent = outboxEventJpaRepository.findById(publishedEventId).orElseThrow();

    assertThat(failedEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(failedEvent.getAttempts()).isEqualTo(1);
    assertThat(failedEvent.getLastError())
        .isEqualTo(CommonErrorCode.OUTBOX_EVENT_PUBLISH_ERROR_EXCEPTION.getMessage());
    assertThat(failedEvent.getNextAttemptAt()).isNotNull();
    assertThat(failedEvent.getPublishedAt()).isNull();
    assertThat(publishedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(publishedEvent.getAttempts()).isZero();
    assertThat(publishedEvent.getLastError()).isNull();
    assertThat(publishedEvent.getNextAttemptAt()).isNull();
    assertThat(publishedEvent.getPublishedAt()).isNotNull();
    assertThat(messages).singleElement().satisfies(message -> {
      assertThat(message.getPayload().eventId()).isEqualTo(publishedEventId);
      assertThat(message.getPayload().eventType()).isEqualTo("PARTIAL_SUCCESS_EVENT");
    });
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
  }

  private void register() {
    var requestBody =
        """
        {
          "name": "Integration User",
          "email": "outbox-sqs-failure.integration@example.com",
          "password": "secure-password"
        }
        """;

    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "outbox-sqs-publication-failure")
        .body(requestBody)
        .when()
        .post(REGISTER_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(201);
  }

  private OutboxEventEntity outboxEvent(UUID id, String eventType, Instant createdAt) {
    return new OutboxEventEntity(
        id,
        IamOutboxEventTypes.AGGREGATE_USER,
        id.toString(),
        eventType,
        1,
        """
        {
          "userId": "%s",
          "email": "%s@example.com",
          "reason": "REGISTER"
        }
        """
            .formatted(id, id),
        OutboxEventStatus.PENDING,
        0,
        null,
        createdAt,
        null,
        null);
  }
}
