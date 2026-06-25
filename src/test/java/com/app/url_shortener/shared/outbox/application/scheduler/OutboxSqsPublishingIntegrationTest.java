package com.app.url_shortener.shared.outbox.application.scheduler;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.infrastructure.repository.EmailDispatchJpaRepository;
import com.app.url_shortener.iam.infrastructure.repository.EmailVerificationTokenJpaRepository;
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
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.test.context.TestPropertySource;

@TestPropertySource(
    properties = {
      "spring.cloud.aws.sqs.listener.auto-startup=false",
      "app.outbox.publisher.enabled=true",
      "app.outbox.publisher.batch-size=2",
      "app.outbox.publisher.fixed-delay=1h",
      "app.outbox.publisher.initial-delay=1h"
    })
@DisplayName("Testes de Integração - Publicação da outbox na SQS")
class OutboxSqsPublishingIntegrationTest extends AbstractIntegrationTest {

  private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
  private static final String QUEUE_NAME = "email-verification-events-queue";

  private final SqsTemplate sqsTemplate;
  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final EmailDispatchJpaRepository emailDispatchJpaRepository;
  private final EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository;
  private final OutboxPublisherScheduler outboxPublisherScheduler;

  @Autowired
  OutboxSqsPublishingIntegrationTest(
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
  @DisplayName("Deve buscar somente o limite do lote e publicar envelopes reais na SQS")
  void shouldPublishOnlyConfiguredBatchSizeToSqs() {
    // Arrange
    register("first-batch.integration@example.com", "outbox-batch-first");
    register("second-batch.integration@example.com", "outbox-batch-second");
    register("third-batch.integration@example.com", "outbox-batch-third");

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
    var events = outboxEventJpaRepository.findAll();
    var publishedEvents =
        events.stream()
            .filter(event -> event.getStatus() == OutboxEventStatus.PUBLISHED)
            .toList();
    var pendingEvents =
        events.stream()
            .filter(event -> event.getStatus() == OutboxEventStatus.PENDING)
            .toList();
    Set<?> publishedEventIds =
        publishedEvents.stream().map(OutboxEventEntity::getId).collect(Collectors.toSet());
    Set<?> messageEventIds =
        messages.stream()
            .map(Message::getPayload)
            .map(OutboxMessageEnvelope::eventId)
            .collect(Collectors.toSet());

    assertThat(events).hasSize(3);
    assertThat(publishedEvents).hasSize(2).allSatisfy(this::assertPublished);
    assertThat(pendingEvents)
        .singleElement()
        .satisfies(
            event -> {
              assertThat(event.getAttempts()).isZero();
              assertThat(event.getPublishedAt()).isNull();
            });
    assertThat(messages).hasSize(2);
    assertThat(messageEventIds).isEqualTo(publishedEventIds);
    assertThat(messages)
        .allSatisfy(message -> assertEnvelope(message.getPayload()));
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName("Deve ignorar eventos não elegíveis e publicar somente eventos pendentes aptos")
  void shouldIgnoreIneligibleEventsAndPublishOnlyEligiblePendingEvents() {
    // Arrange
    var now = Instant.now();
    var eligibleEventId = UUID.fromString("019b1ec0-4f2a-7d90-9c10-111111111111");
    var publishedEventId = UUID.fromString("019b1ec0-4f2a-7d90-9c10-222222222222");
    var failedEventId = UUID.fromString("019b1ec0-4f2a-7d90-9c10-333333333333");
    var futureRetryEventId = UUID.fromString("019b1ec0-4f2a-7d90-9c10-444444444444");

    outboxEventJpaRepository.saveAll(
        List.of(
            outboxEvent(
                eligibleEventId,
                OutboxEventStatus.PENDING,
                0,
                null,
                now.minusSeconds(40),
                null,
                null),
            outboxEvent(
                publishedEventId,
                OutboxEventStatus.PUBLISHED,
                0,
                null,
                now.minusSeconds(30),
                now.minusSeconds(20),
                null),
            outboxEvent(
                failedEventId,
                OutboxEventStatus.FAILED,
                1,
                "permanent failure",
                now.minusSeconds(20),
                null,
                null),
            outboxEvent(
                futureRetryEventId,
                OutboxEventStatus.PENDING,
                1,
                "temporary failure",
                now.minusSeconds(10),
                null,
                now.plusSeconds(60))));

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
    var eligibleEvent = outboxEventJpaRepository.findById(eligibleEventId).orElseThrow();
    var publishedEvent = outboxEventJpaRepository.findById(publishedEventId).orElseThrow();
    var failedEvent = outboxEventJpaRepository.findById(failedEventId).orElseThrow();
    var futureRetryEvent = outboxEventJpaRepository.findById(futureRetryEventId).orElseThrow();

    assertPublished(eligibleEvent);
    assertThat(messages).singleElement().satisfies(message -> {
      assertThat(message.getPayload().eventId()).isEqualTo(eligibleEventId);
      assertEnvelope(message.getPayload());
    });
    assertThat(publishedEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(publishedEvent.getPublishedAt()).isEqualTo(now.minusSeconds(20));
    assertThat(failedEvent.getStatus()).isEqualTo(OutboxEventStatus.FAILED);
    assertThat(failedEvent.getAttempts()).isEqualTo(1);
    assertThat(futureRetryEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(futureRetryEvent.getAttempts()).isEqualTo(1);
    assertThat(futureRetryEvent.getNextAttemptAt()).isEqualTo(now.plusSeconds(60));
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName("Deve distribuir eventos entre schedulers concorrentes sem duplicar publicações")
  void shouldDistributeEventsBetweenConcurrentSchedulersWithoutDuplicatePublications()
      throws Exception {
    // Arrange
    register("concurrent-outbox-first.integration@example.com", "concurrent-outbox-first");
    register("concurrent-outbox-second.integration@example.com", "concurrent-outbox-second");
    register("concurrent-outbox-third.integration@example.com", "concurrent-outbox-third");
    register("concurrent-outbox-fourth.integration@example.com", "concurrent-outbox-fourth");
    var workersReady = new CountDownLatch(2);
    var startSignal = new CountDownLatch(1);

    // Act
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstScheduler =
          executor.submit(
              () -> {
                publishAfterSignal(workersReady, startSignal);
                return null;
              });
      var secondScheduler =
          executor.submit(
              () -> {
                publishAfterSignal(workersReady, startSignal);
                return null;
              });

      if (!workersReady.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent outbox schedulers did not become ready");
      }
      startSignal.countDown();
      firstScheduler.get(10, TimeUnit.SECONDS);
      secondScheduler.get(10, TimeUnit.SECONDS);
    }

    Collection<Message<OutboxMessageEnvelope>> messages =
        sqsTemplate.receiveMany(
            options ->
                options
                    .queue(QUEUE_NAME)
                    .maxNumberOfMessages(10)
                    .pollTimeout(Duration.ofSeconds(5)),
            OutboxMessageEnvelope.class);

    // Assert
    List<OutboxEventEntity> events = outboxEventJpaRepository.findAll();
    Set<?> eventIds =
        events.stream().map(OutboxEventEntity::getId).collect(Collectors.toSet());
    Set<?> messageEventIds =
        messages.stream()
            .map(Message::getPayload)
            .map(OutboxMessageEnvelope::eventId)
            .collect(Collectors.toSet());

    assertThat(events).hasSize(4).allSatisfy(this::assertPublished);
    assertThat(messages).hasSize(4);
    assertThat(messageEventIds).hasSize(4).isEqualTo(eventIds);
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
  }

  private void register(String email, String idempotencyKey) {
    var requestBody =
        """
        {
          "name": "Integration User",
          "email": "%s",
          "password": "secure-password"
        }
        """
            .formatted(email);

    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", idempotencyKey)
        .body(requestBody)
        .when()
        .post(REGISTER_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(201);
  }

  private void assertPublished(OutboxEventEntity event) {
    assertThat(event.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(event.getAttempts()).isZero();
    assertThat(event.getPublishedAt()).isNotNull();
    assertThat(event.getLastError()).isNull();
    assertThat(event.getNextAttemptAt()).isNull();
  }

  private void assertEnvelope(OutboxMessageEnvelope envelope) {
    assertThat(envelope.eventType()).isEqualTo(IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED);
    assertThat(envelope.aggregateType()).isEqualTo(IamOutboxEventTypes.AGGREGATE_USER);
    assertThat(envelope.aggregateId()).isNotBlank();
    assertThat(envelope.schemaVersion()).isEqualTo(1);
    assertThat(envelope.occurredAt()).isNotNull();
    assertThat(envelope.payload().path("userId").asString()).isNotBlank();
    assertThat(envelope.payload().path("email").asString()).endsWith("@example.com");
    assertThat(envelope.payload().path("reason").asString()).isEqualTo("REGISTER");
    assertThat(envelope.payload().has("otp")).isFalse();
  }

  private void publishAfterSignal(CountDownLatch workersReady, CountDownLatch startSignal)
      throws InterruptedException {
    workersReady.countDown();
    if (!startSignal.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent outbox scheduler start signal was not received");
    }
    outboxPublisherScheduler.publishPendingEvents();
  }

  private OutboxEventEntity outboxEvent(
      UUID id,
      OutboxEventStatus status,
      int attempts,
      String lastError,
      Instant createdAt,
      Instant publishedAt,
      Instant nextAttemptAt) {
    return new OutboxEventEntity(
        id,
        IamOutboxEventTypes.AGGREGATE_USER,
        id.toString(),
        IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED,
        1,
        """
        {
          "userId": "%s",
          "email": "%s@example.com",
          "reason": "REGISTER"
        }
        """
            .formatted(id, id),
        status,
        attempts,
        lastError,
        createdAt,
        publishedAt,
        nextAttemptAt);
  }
}
