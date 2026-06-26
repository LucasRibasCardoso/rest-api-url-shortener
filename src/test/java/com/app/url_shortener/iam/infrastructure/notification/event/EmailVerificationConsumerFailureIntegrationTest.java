package com.app.url_shortener.iam.infrastructure.notification.event;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.ImmediateSqsRetryTestConfiguration;
import com.app.url_shortener.config.LocalStackContainerSupport;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedPayload;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.infrastructure.repository.EmailDispatchJpaRepository;
import com.app.url_shortener.iam.infrastructure.repository.EmailVerificationTokenJpaRepository;
import com.app.url_shortener.iam.infrastructure.repository.UserJpaRepository;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import com.app.url_shortener.shared.outbox.application.scheduler.OutboxPublisherScheduler;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
import io.awspring.cloud.sqs.listener.acknowledgement.Acknowledgement;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import io.restassured.http.ContentType;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.Message;
import org.springframework.test.context.TestPropertySource;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.DeleteIdentityRequest;
import software.amazon.awssdk.services.ses.model.VerifyEmailIdentityRequest;
import software.amazon.awssdk.services.sqs.SqsAsyncClient;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@Import(ImmediateSqsRetryTestConfiguration.class)
@TestPropertySource(
    properties = {
      "app.iam.email-verification.sender=ses",
      "spring.cloud.aws.sqs.listener.auto-startup=true",
      "app.outbox.publisher.enabled=true",
      "app.outbox.publisher.fixed-delay=1h",
      "app.outbox.publisher.initial-delay=1h"
    })
@DisplayName("Testes de Integração - Falhas no consumo de verificação de e-mail")
class EmailVerificationConsumerFailureIntegrationTest extends AbstractIntegrationTest {

  private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
  private static final String FROM_EMAIL = "no-reply@url-shortener.local";
  private static final String QUEUE_NAME = "email-verification-events-queue";
  private static final String DLQ_NAME = "email-verification-events-dlq";
  private static final String REGISTER_SUCCESS_MESSAGE = "Enviamos um código de verificação para o seu e-mail.";
  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(15);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final SesClient sesClient;
  private final SqsTemplate sqsTemplate;
  private final SqsAsyncClient sqsAsyncClient;
  private final ObjectMapper objectMapper;
  private final UserJpaRepository userJpaRepository;
  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final EmailDispatchJpaRepository emailDispatchJpaRepository;
  private final EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository;
  private final OutboxPublisherScheduler outboxPublisherScheduler;
  private final UserTestDataFactory userTestDataFactory;

  @Autowired
  EmailVerificationConsumerFailureIntegrationTest(
      SesClient sesClient,
      SqsTemplate sqsTemplate,
      SqsAsyncClient sqsAsyncClient,
      ObjectMapper objectMapper,
      UserJpaRepository userJpaRepository,
      OutboxEventJpaRepository outboxEventJpaRepository,
      EmailDispatchJpaRepository emailDispatchJpaRepository,
      EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository,
      OutboxPublisherScheduler outboxPublisherScheduler,
      UserTestDataFactory userTestDataFactory) {
    this.sesClient = sesClient;
    this.sqsTemplate = sqsTemplate;
    this.sqsAsyncClient = sqsAsyncClient;
    this.objectMapper = objectMapper;
    this.userJpaRepository = userJpaRepository;
    this.outboxEventJpaRepository = outboxEventJpaRepository;
    this.emailDispatchJpaRepository = emailDispatchJpaRepository;
    this.emailVerificationTokenJpaRepository = emailVerificationTokenJpaRepository;
    this.outboxPublisherScheduler = outboxPublisherScheduler;
    this.userTestDataFactory = userTestDataFactory;
  }

  @Test
  @DisplayName("Deve mover evento para DLQ após falhas permanentes no SES")
  void shouldMoveEventToDlqAfterPermanentSesFailures() {
    // Arrange
    String email = "permanent-ses-failure.integration@example.com";
    sesClient.deleteIdentity(DeleteIdentityRequest.builder().identity(FROM_EMAIL).build());
    register(email, "permanent-ses-failure-register");
    var outboxEvent = outboxEventJpaRepository.findAll().getFirst();

    // Act
    outboxPublisherScheduler.publishPendingEvents();

    // Assert
    awaitFailedDispatchInDlq(outboxEvent.getId());

    var dlqMessage = receiveDlqMessage();
    var dispatch = emailDispatchJpaRepository.findByEventId(outboxEvent.getId()).orElseThrow();
    var token =
        emailVerificationTokenJpaRepository
            .findById(dispatch.getVerificationTokenId())
            .orElseThrow();
    var user = userJpaRepository.findByEmail(email).orElseThrow();

    assertThat(dlqMessage.getPayload().eventId()).isEqualTo(outboxEvent.getId());
    assertThat(outboxEventJpaRepository.findById(outboxEvent.getId()).orElseThrow().getStatus())
        .isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(dispatch.getProviderMessageId()).isNull();
    assertThat(dispatch.getFailedAt()).isNotNull();
    assertThat(dispatch.getLastErrorCode())
        .isEqualTo(IamErrorCode.AUTH_EMAIL_VERIFICATION_SEND_FAILED.name());
    assertThat(token.getConsumedAt()).isNull();
    assertThat(token.getRevokedAt()).isNull();
    assertThat(user.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    assertThat(user.isEmailVerified()).isFalse();
    assertThat(queueMessageCount(QUEUE_NAME)).isZero();
  }

  @Test
  @DisplayName("Deve mover envelope incompatível para DLQ sem criar estado IAM")
  void shouldMoveInvalidEnvelopeToDlqWithoutCreatingIamState() {
    // Arrange
    UUID eventId = UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13101");
    var invalidEnvelope =
        new OutboxMessageEnvelope(
            eventId,
            IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED,
            99,
            IamOutboxEventTypes.AGGREGATE_USER,
            UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13102").toString(),
            Instant.now(),
            objectMapper.createObjectNode());

    // Act
    sqsTemplate.send(QUEUE_NAME, invalidEnvelope);

    // Assert
    awaitDlqMessage(eventId);
    assertNoIamStateCreated();
  }

  @Test
  @DisplayName("Deve mover evento com tipo incompatível para DLQ sem criar estado IAM")
  void shouldMoveUnsupportedEventTypeToDlqWithoutCreatingIamState() {
    // Arrange
    UUID eventId = UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13111");
    var invalidEnvelope =
        new OutboxMessageEnvelope(
            eventId,
            "UNSUPPORTED_EVENT",
            1,
            IamOutboxEventTypes.AGGREGATE_USER,
            UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13112").toString(),
            Instant.now(),
            validPayload(
                UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13112"),
                "unsupported-event-type.integration@example.com",
                EmailDispatchReason.REGISTER));

    // Act
    sqsTemplate.send(QUEUE_NAME, invalidEnvelope);

    // Assert
    awaitDlqMessage(eventId);
    assertNoIamStateCreated();
  }

  @Test
  @DisplayName("Deve mover payload inválido para DLQ sem criar estado IAM")
  void shouldMoveInvalidPayloadToDlqWithoutCreatingIamState() {
    // Arrange
    UUID eventId = UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13121");
    var invalidEnvelope =
        new OutboxMessageEnvelope(
            eventId,
            IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED,
            1,
            IamOutboxEventTypes.AGGREGATE_USER,
            UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13122").toString(),
            Instant.now(),
            objectMapper
                .createObjectNode()
                .put("email", "invalid-payload.integration@example.com"));

    // Act
    sqsTemplate.send(QUEUE_NAME, invalidEnvelope);

    // Assert
    awaitDlqMessage(eventId);
    assertNoIamStateCreated();
  }

  @Test
  @DisplayName("Deve ignorar evento para usuário inexistente sem criar dispatch ou token")
  void shouldIgnoreEventForMissingUserWithoutCreatingDispatchOrToken() {
    // Arrange
    UUID eventId = UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13131");
    UUID userId = UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13132");
    var envelope =
        validEnvelope(
            eventId,
            userId,
            "missing-user-event.integration@example.com",
            EmailDispatchReason.REGISTER);

    // Act
    sqsTemplate.send(QUEUE_NAME, envelope);

    // Assert
    awaitQueueDrained();
    assertThat(userJpaRepository.count()).isZero();
    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
    assertThat(capturedMessageCount()).isZero();
    assertThat(queueMessageCount(DLQ_NAME)).isZero();
  }

  @Test
  @DisplayName("Deve ignorar evento com e-mail divergente sem criar dispatch ou token")
  void shouldIgnoreEventWithMismatchedEmailWithoutCreatingDispatchOrToken() {
    // Arrange
    var user =
        userTestDataFactory.createPendingUser(
            "pending-mismatch.integration@example.com", "secure-password");
    UUID eventId = UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13141");
    var envelope =
        validEnvelope(
            eventId,
            user.getId(),
            "different-mismatch.integration@example.com",
            EmailDispatchReason.REGISTER);

    // Act
    sqsTemplate.send(QUEUE_NAME, envelope);

    // Assert
    awaitQueueDrained();
    var savedUser = userJpaRepository.findById(user.getId()).orElseThrow();

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    assertThat(savedUser.isEmailVerified()).isFalse();
    assertThat(userJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
    assertThat(capturedMessageCount()).isZero();
    assertThat(queueMessageCount(DLQ_NAME)).isZero();
  }

  @Test
  @DisplayName("Deve ignorar evento para usuário ativo sem criar dispatch ou token")
  void shouldIgnoreEventForActiveUserWithoutCreatingDispatchOrToken() {
    // Arrange
    var user =
        userTestDataFactory.createActiveUser(
            "active-user-event.integration@example.com", "secure-password");
    UUID eventId = UUID.fromString("019b7af8-2092-7ae1-89cc-cfef16f13151");
    var envelope =
        validEnvelope(
            eventId,
            user.getId(),
            user.getEmail(),
            EmailDispatchReason.REGISTER);

    // Act
    sqsTemplate.send(QUEUE_NAME, envelope);

    // Assert
    awaitQueueDrained();
    var savedUser = userJpaRepository.findById(user.getId()).orElseThrow();

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(savedUser.isEmailVerified()).isTrue();
    assertThat(userJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
    assertThat(capturedMessageCount()).isZero();
    assertThat(queueMessageCount(DLQ_NAME)).isZero();
  }

  @Test
  @DisplayName("Deve processar evento redirecionado da DLQ após recuperação do SES")
  void shouldProcessRedrivenDlqEventAfterSesRecovery() {
    // Arrange
    String email = "ses-dlq-redrive.integration@example.com";
    sesClient.deleteIdentity(DeleteIdentityRequest.builder().identity(FROM_EMAIL).build());
    register(email, "ses-dlq-redrive-register");
    var outboxEvent = outboxEventJpaRepository.findAll().getFirst();
    outboxPublisherScheduler.publishPendingEvents();

    awaitFailedDispatchInDlq(outboxEvent.getId());

    var failedDispatch =
        emailDispatchJpaRepository.findByEventId(outboxEvent.getId()).orElseThrow();
    var dlqMessage = receiveDlqMessage();

    // Act
    Acknowledgement.acknowledge(dlqMessage);
    sesClient.verifyEmailIdentity(
        VerifyEmailIdentityRequest.builder().emailAddress(FROM_EMAIL).build());
    sqsTemplate.send(QUEUE_NAME, dlqMessage.getPayload());

    // Assert
    await()
        .atMost(ASYNC_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(
            () -> {
              var dispatch =
                  emailDispatchJpaRepository.findByEventId(outboxEvent.getId()).orElseThrow();
              assertThat(dispatch.getStatus()).isEqualTo(EmailDispatchStatus.ACCEPTED);
              assertThat(dispatch.getSendAttempts()).isEqualTo(6);
              assertThat(dispatch.getProviderMessageId()).isNotBlank();
              assertThat(capturedMessageCount()).isEqualTo(1);
              assertThat(queueMessageCount(QUEUE_NAME)).isZero();
              assertThat(queueMessageCount(DLQ_NAME)).isZero();
            });

    var acceptedDispatch =
        emailDispatchJpaRepository.findByEventId(outboxEvent.getId()).orElseThrow();

    assertThat(dlqMessage.getPayload().eventId()).isEqualTo(outboxEvent.getId());
    assertThat(acceptedDispatch.getId()).isEqualTo(failedDispatch.getId());
    assertThat(acceptedDispatch.getVerificationTokenId())
        .isEqualTo(failedDispatch.getVerificationTokenId());
    assertThat(acceptedDispatch.getFailedAt()).isNull();
    assertThat(acceptedDispatch.getLastErrorCode()).isNull();
    assertThat(acceptedDispatch.getLastErrorMessage()).isNull();
    assertThat(emailDispatchJpaRepository.count()).isEqualTo(1);
    assertThat(emailVerificationTokenJpaRepository.count()).isEqualTo(1);
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
        .statusCode(201)
        .contentType(ContentType.JSON)
        .body("message", is(REGISTER_SUCCESS_MESSAGE));
  }

  private void awaitFailedDispatchInDlq(UUID eventId) {
    await()
        .atMost(ASYNC_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(
            () -> {
              assertThat(visibleMessageCount(DLQ_NAME)).isEqualTo(1);
              var dispatch = emailDispatchJpaRepository.findByEventId(eventId).orElseThrow();
              assertThat(dispatch.getStatus()).isEqualTo(EmailDispatchStatus.FAILED);
              assertThat(dispatch.getSendAttempts()).isEqualTo(5);
            });
  }

  private Message<OutboxMessageEnvelope> receiveDlqMessage() {
    return sqsTemplate
        .receive(
            options -> options.queue(DLQ_NAME).pollTimeout(Duration.ofSeconds(2)),
            OutboxMessageEnvelope.class)
        .orElseThrow();
  }

  private int visibleMessageCount(String queueName) {
    return queueAttribute(queueName, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES);
  }

  private int queueMessageCount(String queueName) {
    return visibleMessageCount(queueName)
            + queueAttribute(queueName, QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE);
  }

  private int queueAttribute(String queueName, QueueAttributeName attributeName) {
    String queueUrl =
        sqsAsyncClient
            .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
            .join()
            .queueUrl();
    String value =
        sqsAsyncClient
            .getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(attributeName)
                    .build())
            .join()
            .attributes()
            .getOrDefault(attributeName, "0");
    return Integer.parseInt(value);
  }

  private int capturedMessageCount() {
    try {
      String encodedFromEmail = URLEncoder.encode(FROM_EMAIL, StandardCharsets.UTF_8);
      URI endpoint = LocalStackContainerSupport.sesEndpoint();
      var request =
          HttpRequest.newBuilder(endpoint.resolve("/_aws/ses?email=" + encodedFromEmail))
              .GET()
              .build();
      var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

      if (response.statusCode() != 200) {
        throw new IllegalStateException(
            "Failed to inspect captured SES messages. statusCode=" + response.statusCode());
      }
      return objectMapper.readTree(response.body()).path("messages").size();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while inspecting captured SES messages", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to inspect captured SES messages", exception);
    }
  }

  private void awaitDlqMessage(UUID eventId) {
    await()
        .atMost(ASYNC_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(() -> assertThat(visibleMessageCount(DLQ_NAME)).isEqualTo(1));

    var dlqMessage = receiveDlqMessage();

    assertThat(dlqMessage.getPayload().eventId()).isEqualTo(eventId);
    assertThat(queueMessageCount(QUEUE_NAME)).isZero();
  }

  private void awaitQueueDrained() {
    await()
        .atMost(ASYNC_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(() -> assertThat(queueMessageCount(QUEUE_NAME)).isZero());
  }

  private void assertNoIamStateCreated() {
    assertThat(userJpaRepository.count()).isZero();
    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(emailDispatchJpaRepository.count()).isZero();
    assertThat(emailVerificationTokenJpaRepository.count()).isZero();
    assertThat(capturedMessageCount()).isZero();
  }

  private OutboxMessageEnvelope validEnvelope(
      UUID eventId,
      UUID userId,
      String email,
      EmailDispatchReason reason) {
    return new OutboxMessageEnvelope(
        eventId,
        IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED,
        1,
        IamOutboxEventTypes.AGGREGATE_USER,
        userId.toString(),
        Instant.now(),
        validPayload(userId, email, reason));
  }

  private JsonNode validPayload(UUID userId, String email, EmailDispatchReason reason) {
    return objectMapper.valueToTree(new EmailVerificationRequestedPayload(userId, email, reason));
  }

}
