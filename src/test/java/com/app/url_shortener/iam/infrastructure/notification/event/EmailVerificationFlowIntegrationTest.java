package com.app.url_shortener.iam.infrastructure.notification.event;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.LocalStackContainerSupport;
import com.app.url_shortener.iam.application.event.EmailVerificationRequestedEvent;
import com.app.url_shortener.iam.application.port.output.EmailDispatchRepositoryPort;
import com.app.url_shortener.iam.application.service.EmailDispatchVerificationService;
import com.app.url_shortener.iam.application.service.model.PreparedEmailVerificationDispatch;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.EmailDispatchStatus;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.infrastructure.entity.EmailDispatchEntity;
import com.app.url_shortener.iam.infrastructure.repository.EmailDispatchJpaRepository;
import com.app.url_shortener.iam.infrastructure.repository.EmailVerificationTokenJpaRepository;
import com.app.url_shortener.iam.infrastructure.repository.UserJpaRepository;
import com.app.url_shortener.shared.outbox.application.message.OutboxMessageEnvelope;
import com.app.url_shortener.shared.outbox.application.scheduler.OutboxPublisherScheduler;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import com.app.url_shortener.shared.outbox.infrastructure.entity.OutboxEventEntity;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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

@TestPropertySource(
    properties = {
      "app.iam.email-verification.sender=ses",
      "app.iam.email-verification.sending-timeout=100ms",
      "spring.cloud.aws.sqs.listener.auto-startup=true",
      "app.outbox.publisher.enabled=true",
      "app.outbox.publisher.fixed-delay=1h",
      "app.outbox.publisher.initial-delay=1h"
    })
@DisplayName("Testes de Integração - Fluxo de verificação de e-mail")
class EmailVerificationFlowIntegrationTest extends AbstractIntegrationTest {

  private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
  private static final String RESEND_ENDPOINT = "/api/v1/auth/resend-verification";
  private static final String VERIFY_ENDPOINT = "/api/v1/auth/verify-email";
  private static final String FROM_EMAIL = "no-reply@url-shortener.local";
  private static final String QUEUE_NAME = "email-verification-events-queue";
  private static final String REGISTER_SUCCESS_MESSAGE = "Enviamos um código de verificação para o seu e-mail.";
  private static final String RESEND_SUCCESS_MESSAGE = "Enviamos um novo código de verificação para o seu e-mail.";
  private static final String VERIFY_SUCCESS_MESSAGE = "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";
  private static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(10);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(200);
  private static final Pattern VERIFICATION_CODE_PATTERN = Pattern.compile("\\b(\\d{6})\\b");

  private final HttpClient httpClient = HttpClient.newHttpClient();
  private final ObjectMapper objectMapper;
  private final SesClient sesClient;
  private final SqsTemplate sqsTemplate;
  private final SqsAsyncClient sqsAsyncClient;
  private final UserJpaRepository userJpaRepository;
  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final EmailDispatchJpaRepository emailDispatchJpaRepository;
  private final EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository;
  private final OutboxPublisherScheduler outboxPublisherScheduler;
  private final EmailDispatchRepositoryPort emailDispatchRepositoryPort;
  private final EmailDispatchVerificationService emailDispatchVerificationService;

  @Autowired
  EmailVerificationFlowIntegrationTest(
      ObjectMapper objectMapper,
      SesClient sesClient,
      SqsTemplate sqsTemplate,
      SqsAsyncClient sqsAsyncClient,
      UserJpaRepository userJpaRepository,
      OutboxEventJpaRepository outboxEventJpaRepository,
      EmailDispatchJpaRepository emailDispatchJpaRepository,
      EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository,
      OutboxPublisherScheduler outboxPublisherScheduler,
      EmailDispatchRepositoryPort emailDispatchRepositoryPort,
      EmailDispatchVerificationService emailDispatchVerificationService) {
    this.objectMapper = objectMapper;
    this.sesClient = sesClient;
    this.sqsTemplate = sqsTemplate;
    this.sqsAsyncClient = sqsAsyncClient;
    this.userJpaRepository = userJpaRepository;
    this.outboxEventJpaRepository = outboxEventJpaRepository;
    this.emailDispatchJpaRepository = emailDispatchJpaRepository;
    this.emailVerificationTokenJpaRepository = emailVerificationTokenJpaRepository;
    this.outboxPublisherScheduler = outboxPublisherScheduler;
    this.emailDispatchRepositoryPort = emailDispatchRepositoryPort;
    this.emailDispatchVerificationService = emailDispatchVerificationService;
  }

  @Test
  @DisplayName("Deve registrar, enviar o código e retornar 200 ao verificar o e-mail")
  void shouldRegisterSendCodeAndVerifyEmail() {
    // Arrange
    String email = "complete-register-flow.integration@example.com";
    var registerRequestBody = registerRequestBody(email);

    // Act
    register(registerRequestBody, "complete-register-flow");
    OutboxEventEntity outboxEvent = getFirstOutboxEvent();
    outboxPublisherScheduler.publishPendingEvents();
    EmailDispatchEntity dispatch = awaitAcceptedDispatch(outboxEvent.getId());
    String verificationCode = verificationCodeFromCapturedEmail(dispatch.getProviderMessageId());
    verifyEmail(email, verificationCode, "verify-complete-register-flow");

    // Assert
    var savedUser = userJpaRepository.findByEmailWithRoles(email).orElseThrow();
    var savedOutboxEvent = outboxEventJpaRepository.findById(outboxEvent.getId()).orElseThrow();
    var savedDispatch = emailDispatchJpaRepository.findById(dispatch.getId()).orElseThrow();
    var savedToken = emailVerificationTokenJpaRepository.findById(dispatch.getVerificationTokenId()).orElseThrow();

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(savedUser.isEmailVerified()).isTrue();
    assertThat(savedUser.getRoles()).singleElement().satisfies(role -> assertThat(role.getName()).isEqualTo("USER"));
    assertThat(savedOutboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(savedOutboxEvent.getPublishedAt()).isNotNull();
    assertThat(savedDispatch.getStatus()).isEqualTo(EmailDispatchStatus.ACCEPTED);
    assertThat(savedDispatch.getReason()).isEqualTo(EmailDispatchReason.REGISTER);
    assertThat(savedDispatch.getSendAttempts()).isEqualTo(1);
    assertThat(savedDispatch.getAcceptedAt()).isNotNull();
    assertThat(savedToken.getConsumedAt()).isNotNull();
    assertThat(savedToken.getRevokedAt()).isNull();
    assertThat(capturedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("Deve reenviar, revogar o código anterior e retornar 200 ao verificar o novo código")
  void shouldResendRevokePreviousCodeAndVerifyNewCode() {
    // Arrange
    String email = "complete-resend-flow.integration@example.com";
    var registerRequestBody = registerRequestBody(email);
    var resendRequestBody =
        """
        {
          "email": "%s"
        }
        """
            .formatted(email);

    // Act
    register(registerRequestBody, "complete-resend-register");
    OutboxEventEntity registerEvent = getFirstOutboxEvent();
    outboxPublisherScheduler.publishPendingEvents();
    EmailDispatchEntity registerDispatch = awaitAcceptedDispatch(registerEvent.getId());

    resend(resendRequestBody, "complete-resend-request");
    OutboxEventEntity resendEvent = outboxEventExcluding(registerEvent.getId());
    outboxPublisherScheduler.publishPendingEvents();
    EmailDispatchEntity resendDispatch = awaitAcceptedDispatch(resendEvent.getId());
    String newVerificationCode = verificationCodeFromCapturedEmail(resendDispatch.getProviderMessageId());
    verifyEmail(email, newVerificationCode, "verify-complete-resend-flow");

    // Assert
    var savedUser = userJpaRepository.findByEmailWithRoles(email).orElseThrow();
    var previousToken =
        emailVerificationTokenJpaRepository
            .findById(registerDispatch.getVerificationTokenId())
            .orElseThrow();
    var currentToken =
        emailVerificationTokenJpaRepository
            .findById(resendDispatch.getVerificationTokenId())
            .orElseThrow();

    assertThat(outboxEventJpaRepository.findById(resendEvent.getId()).orElseThrow().getStatus())
        .isEqualTo(OutboxEventStatus.PUBLISHED);
    assertThat(emailDispatchJpaRepository.count()).isEqualTo(2);
    assertThat(emailVerificationTokenJpaRepository.count()).isEqualTo(2);
    assertThat(registerDispatch.getReason()).isEqualTo(EmailDispatchReason.REGISTER);
    assertThat(resendDispatch.getReason()).isEqualTo(EmailDispatchReason.RESEND);
    assertThat(previousToken.getRevokedAt()).isNotNull();
    assertThat(previousToken.getConsumedAt()).isNull();
    assertThat(currentToken.getRevokedAt()).isNull();
    assertThat(currentToken.getConsumedAt()).isNotNull();
    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(savedUser.isEmailVerified()).isTrue();
    assertThat(capturedMessages()).hasSize(2);
  }

  @Test
  @DisplayName("Deve ignorar o redelivery de evento cujo envio já foi aceito")
  void shouldIgnoreRedeliveryForAcceptedDispatch() {
    // Arrange
    String email = "duplicate-event-flow.integration@example.com";
    var registerRequestBody = registerRequestBody(email);
    register(registerRequestBody, "duplicate-event-register");
    OutboxEventEntity outboxEvent = getFirstOutboxEvent();
    outboxPublisherScheduler.publishPendingEvents();
    EmailDispatchEntity initialDispatch = awaitAcceptedDispatch(outboxEvent.getId());
    OutboxMessageEnvelope duplicateEnvelope = toEnvelope(outboxEvent);

    // Act
    sqsTemplate.send(QUEUE_NAME, duplicateEnvelope);

    // Assert
    awaitQueueDrained();
    var savedDispatch = emailDispatchJpaRepository.findById(initialDispatch.getId()).orElseThrow();

    assertThat(emailDispatchJpaRepository.count()).isEqualTo(1);
    assertThat(emailVerificationTokenJpaRepository.count()).isEqualTo(1);
    assertThat(savedDispatch.getStatus()).isEqualTo(EmailDispatchStatus.ACCEPTED);
    assertThat(savedDispatch.getSendAttempts()).isEqualTo(1);
    assertThat(savedDispatch.getProviderMessageId()).isEqualTo(initialDispatch.getProviderMessageId());
    assertThat(capturedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("Deve recuperar envio após falha transitória do SES")
  void shouldRecoverFailedDispatchAfterSesBecomesAvailable() {
    // Arrange
    String email = "transient-ses-failure.integration@example.com";
    var registerRequestBody = registerRequestBody(email);
    sesClient.deleteIdentity(DeleteIdentityRequest.builder().identity(FROM_EMAIL).build());

    // Act
    register(registerRequestBody, "transient-ses-failure-register");
    OutboxEventEntity outboxEvent = getFirstOutboxEvent();
    OutboxMessageEnvelope envelope = toEnvelope(outboxEvent);
    outboxPublisherScheduler.publishPendingEvents();
    EmailDispatchEntity failedDispatch = awaitFailedDispatch(outboxEvent.getId());

    sesClient.verifyEmailIdentity(
        VerifyEmailIdentityRequest.builder().emailAddress(FROM_EMAIL).build());
    sqsTemplate.send(QUEUE_NAME, envelope);
    EmailDispatchEntity acceptedDispatch = awaitAcceptedDispatch(outboxEvent.getId());

    // Assert
    assertThat(acceptedDispatch.getId()).isEqualTo(failedDispatch.getId());
    assertThat(acceptedDispatch.getStatus()).isEqualTo(EmailDispatchStatus.ACCEPTED);
    assertThat(acceptedDispatch.getSendAttempts()).isEqualTo(2);
    assertThat(acceptedDispatch.getFailedAt()).isNull();
    assertThat(acceptedDispatch.getLastErrorCode()).isNull();
    assertThat(acceptedDispatch.getLastErrorMessage()).isNull();
    assertThat(acceptedDispatch.getProviderMessageId()).isNotBlank();
    assertThat(emailVerificationTokenJpaRepository.count()).isEqualTo(1);
    assertThat(capturedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("Deve recuperar dispatch SENDING após expirar o timeout de processamento")
  void shouldRecoverStaleSendingDispatch() {
    // Arrange
    String email = "stale-sending.integration@example.com";
    register(registerRequestBody(email), "stale-sending-register");
    OutboxEventEntity outboxEvent = getFirstOutboxEvent();
    EmailVerificationRequestedEvent event = toEvent(outboxEvent);
    var preparedDispatch = emailDispatchVerificationService.findOrCreate(event);
    Instant sendingStartedAt = Instant.now();
    boolean reserved =
        emailDispatchRepositoryPort.markAsSendingIfAvailable(
            preparedDispatch.dispatch().getId(), sendingStartedAt, Instant.EPOCH);

    await()
        .atMost(Duration.ofSeconds(2))
        .until(() -> Instant.now().isAfter(sendingStartedAt.plusMillis(100)));

    // Act
    outboxPublisherScheduler.publishPendingEvents();
    EmailDispatchEntity recoveredDispatch = awaitAcceptedDispatch(outboxEvent.getId());

    // Assert
    assertThat(reserved).isTrue();
    assertThat(recoveredDispatch.getStatus()).isEqualTo(EmailDispatchStatus.ACCEPTED);
    assertThat(recoveredDispatch.getSendAttempts()).isEqualTo(2);
    assertThat(recoveredDispatch.getProviderMessageId()).isNotBlank();
    assertThat(capturedMessages()).hasSize(1);
  }

  @Test
  @DisplayName("Deve permitir apenas uma reserva concorrente do mesmo dispatch")
  void shouldAllowOnlyOneConcurrentReservationForSameDispatch() throws Exception {
    // Arrange
    String email = "concurrent-dispatch-reservation.integration@example.com";
    register(registerRequestBody(email), "concurrent-dispatch-reservation-register");
    OutboxEventEntity outboxEvent = getFirstOutboxEvent();
    EmailVerificationRequestedEvent event = toEvent(outboxEvent);
    var preparedDispatch = emailDispatchVerificationService.findOrCreate(event);
    UUID dispatchId = preparedDispatch.dispatch().getId();
    Instant reservationTime = Instant.now();
    var workersReady = new CountDownLatch(2);
    var startSignal = new CountDownLatch(1);

    // Act
    List<Boolean> reservationResults;
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstReservation =
          executor.submit(
              () -> reserveAfterSignal(dispatchId, reservationTime, workersReady, startSignal));
      var secondReservation =
          executor.submit(
              () -> reserveAfterSignal(dispatchId, reservationTime, workersReady, startSignal));

      if (!workersReady.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent reservation workers did not become ready");
      }
      startSignal.countDown();
      reservationResults =
          List.of(
              firstReservation.get(5, TimeUnit.SECONDS),
              secondReservation.get(5, TimeUnit.SECONDS));
    }

    // Assert
    var savedDispatch = emailDispatchJpaRepository.findById(dispatchId).orElseThrow();

    assertThat(reservationResults).containsExactlyInAnyOrder(true, false);
    assertThat(savedDispatch.getStatus()).isEqualTo(EmailDispatchStatus.SENDING);
    assertThat(savedDispatch.getSendAttempts()).isEqualTo(1);
    assertThat(savedDispatch.getSendingStartedAt()).isEqualTo(reservationTime);
    assertThat(emailDispatchJpaRepository.count()).isEqualTo(1);
    assertThat(emailVerificationTokenJpaRepository.count()).isEqualTo(1);
    assertThat(capturedMessages()).isEmpty();
  }

  @Test
  @DisplayName("Deve criar somente um token e dispatch para o mesmo evento concorrente")
  void shouldCreateOnlyOneTokenAndDispatchForSameConcurrentEvent() throws Exception {
    // Arrange
    String email = "concurrent-dispatch-creation.integration@example.com";
    register(registerRequestBody(email), "concurrent-dispatch-creation-register");
    OutboxEventEntity outboxEvent = getFirstOutboxEvent();
    EmailVerificationRequestedEvent event = toEvent(outboxEvent);
    var workersReady = new CountDownLatch(2);
    var startSignal = new CountDownLatch(1);

    // Act
    List<PreparedEmailVerificationDispatch> results;
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstPreparation =
          executor.submit(() -> prepareAfterSignal(event, workersReady, startSignal));
      var secondPreparation =
          executor.submit(() -> prepareAfterSignal(event, workersReady, startSignal));

      if (!workersReady.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent dispatch creation workers did not become ready");
      }
      startSignal.countDown();
      results =
          List.of(
              firstPreparation.get(10, TimeUnit.SECONDS),
              secondPreparation.get(10, TimeUnit.SECONDS));
    }

    // Assert
    assertThat(results)
        .extracting(result -> result.dispatch().getId())
        .containsOnly(results.getFirst().dispatch().getId());
    assertThat(results)
        .extracting(result -> result.token().getId())
        .containsOnly(results.getFirst().token().getId());
    assertThat(emailDispatchJpaRepository.count()).isEqualTo(1);
    assertThat(emailVerificationTokenJpaRepository.count()).isEqualTo(1);
    assertThat(emailDispatchJpaRepository.findByEventId(outboxEvent.getId()))
        .isPresent()
        .get()
        .satisfies(
            dispatch -> {
              assertThat(dispatch.getStatus()).isEqualTo(EmailDispatchStatus.PENDING);
              assertThat(dispatch.getSendAttempts()).isZero();
            });
    assertThat(capturedMessages()).isEmpty();
  }

  private String registerRequestBody(String email) {
    return """
        {
          "name": "Integration User",
          "email": "%s",
          "password": "secure-password"
        }
        """
        .formatted(email);
  }

  private void register(String requestBody, String idempotencyKey) {
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

  private void resend(String requestBody, String idempotencyKey) {
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", idempotencyKey)
        .body(requestBody)
        .when()
        .post(RESEND_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(RESEND_SUCCESS_MESSAGE));
  }

  private void verifyEmail(String email, String verificationCode, String idempotencyKey) {
    var requestBody =
        """
        {
          "email": "%s",
          "code": "%s"
        }
        """
            .formatted(email, verificationCode);

    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", idempotencyKey)
        .body(requestBody)
        .when()
        .post(VERIFY_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(VERIFY_SUCCESS_MESSAGE));
  }

  private OutboxEventEntity getFirstOutboxEvent() {
    List<OutboxEventEntity> events = outboxEventJpaRepository.findAll();
    assertThat(events).hasSize(1);
    return events.getFirst();
  }

  private OutboxEventEntity outboxEventExcluding(UUID eventId) {
    return outboxEventJpaRepository.findAll().stream()
        .filter(event -> !event.getId().equals(eventId))
        .findFirst()
        .orElseThrow();
  }

  private EmailDispatchEntity awaitAcceptedDispatch(UUID eventId) {
    await()
        .atMost(ASYNC_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(
            () -> {
              var dispatch = emailDispatchJpaRepository.findByEventId(eventId).orElseThrow();
              assertThat(dispatch.getStatus()).isEqualTo(EmailDispatchStatus.ACCEPTED);
              assertThat(dispatch.getProviderMessageId()).isNotBlank();
              assertThat(findCapturedMessage(dispatch.getProviderMessageId())).isNotNull();
            });

    return emailDispatchJpaRepository.findByEventId(eventId).orElseThrow();
  }

  private EmailDispatchEntity awaitFailedDispatch(UUID eventId) {
    await()
        .atMost(ASYNC_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(
            () -> {
              var dispatch = emailDispatchJpaRepository.findByEventId(eventId).orElseThrow();
              assertThat(dispatch.getStatus()).isEqualTo(EmailDispatchStatus.FAILED);
              assertThat(dispatch.getSendAttempts()).isEqualTo(1);
              assertThat(dispatch.getFailedAt()).isNotNull();
              assertThat(dispatch.getLastErrorCode()).isNotBlank();
            });

    return emailDispatchJpaRepository.findByEventId(eventId).orElseThrow();
  }

  private void awaitQueueDrained() {
    await()
        .atMost(ASYNC_TIMEOUT)
        .pollInterval(POLL_INTERVAL)
        .untilAsserted(() -> assertThat(queueMessageCount()).isZero());
  }

  private int queueMessageCount() {
    String queueUrl =
        sqsAsyncClient
            .getQueueUrl(GetQueueUrlRequest.builder().queueName(QUEUE_NAME).build())
            .join()
            .queueUrl();
    var attributes =
        sqsAsyncClient
            .getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                        QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                    .build())
            .join()
            .attributes();

    return Integer.parseInt(
            attributes.getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0"))
        + Integer.parseInt(
            attributes.getOrDefault(
                QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0"));
  }

  private String verificationCodeFromCapturedEmail(String providerMessageId) {
    JsonNode capturedMessage = findCapturedMessage(providerMessageId);
    if (capturedMessage == null) {
      throw new IllegalStateException("Captured SES message was not found");
    }

    String textBody = capturedMessage.path("Body").path("text_part").asString();
    var matcher = VERIFICATION_CODE_PATTERN.matcher(textBody);
    if (!matcher.find()) {
      throw new IllegalStateException("Verification code was not found in captured SES message");
    }
    return matcher.group(1);
  }

  private JsonNode findCapturedMessage(String providerMessageId) {
    for (JsonNode message : capturedMessages()) {
      if (providerMessageId.equals(message.path("Id").asString())) {
        return message;
      }
    }
    return null;
  }

  private JsonNode capturedMessages() {
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

      JsonNode messages = objectMapper.readTree(response.body()).path("messages");
      if (!messages.isArray()) {
        throw new IllegalStateException("Invalid LocalStack SES diagnostic response");
      }
      return messages;
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException(
          "Interrupted while inspecting captured SES messages", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to inspect captured SES messages", exception);
    }
  }

  private OutboxMessageEnvelope toEnvelope(OutboxEventEntity event) {
    return new OutboxMessageEnvelope(
        event.getId(),
        event.getEventType(),
        event.getSchemaVersion(),
        event.getAggregateType(),
        event.getAggregateId(),
        event.getCreatedAt(),
        objectMapper.readTree(event.getPayload()));
  }

  private EmailVerificationRequestedEvent toEvent(OutboxEventEntity outboxEvent) {
    var envelope = toEnvelope(outboxEvent);
    var payload = envelope.payload();
    return new EmailVerificationRequestedEvent(
        envelope.eventId(),
        UUID.fromString(payload.path("userId").asString()),
        payload.path("email").asString(),
        EmailDispatchReason.valueOf(payload.path("reason").asString()),
        envelope.occurredAt());
  }

  private boolean reserveAfterSignal(
      UUID dispatchId,
      Instant reservationTime,
      CountDownLatch workersReady,
      CountDownLatch startSignal)
      throws InterruptedException {
    workersReady.countDown();
    if (!startSignal.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent reservation start signal was not received");
    }
    return emailDispatchRepositoryPort.markAsSendingIfAvailable(
        dispatchId, reservationTime, Instant.EPOCH);
  }

  private PreparedEmailVerificationDispatch prepareAfterSignal(
      EmailVerificationRequestedEvent event,
      CountDownLatch workersReady,
      CountDownLatch startSignal)
      throws InterruptedException {
    workersReady.countDown();
    if (!startSignal.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent dispatch creation start signal was not received");
    }
    return emailDispatchVerificationService.findOrCreate(event);
  }
}
