package com.app.url_shortener.iam.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import com.app.url_shortener.shared.outbox.infrastructure.entity.OutboxEventEntity;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
import io.restassured.http.ContentType;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Testes de Integração - Endpoint de reenvio de verificação")
class AuthResendVerificationIntegrationTest extends AbstractIntegrationTest {

  private static final String RESEND_ENDPOINT = "/api/v1/auth/resend-verification";
  private static final String PASSWORD = "secure-password";
  private static final String SUCCESS_MESSAGE =
      "Enviamos um novo código de verificação para o seu e-mail.";
  private static final String COOLDOWN_KEY_PREFIX =
      "auth:email-verification:resend-cooldown:";

  private final UserTestDataFactory userTestDataFactory;
  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final StringRedisTemplate stringRedisTemplate;
  private final EmailVerificationPolicy emailVerificationPolicy;
  private final ObjectMapper objectMapper;

  @Autowired
  AuthResendVerificationIntegrationTest(
      UserTestDataFactory userTestDataFactory,
      OutboxEventJpaRepository outboxEventJpaRepository,
      StringRedisTemplate stringRedisTemplate,
      EmailVerificationPolicy emailVerificationPolicy,
      ObjectMapper objectMapper) {
    this.userTestDataFactory = userTestDataFactory;
    this.outboxEventJpaRepository = outboxEventJpaRepository;
    this.stringRedisTemplate = stringRedisTemplate;
    this.emailVerificationPolicy = emailVerificationPolicy;
    this.objectMapper = objectMapper;
  }

  @Test
  @DisplayName("Deve retornar 200, persistir evento de reenvio e reservar cooldown")
  void shouldPersistResendEventAndReserveCooldownForPendingUser() {
    // Arrange
    String email = "pending-resend.integration@example.com";
    UserEntity user = userTestDataFactory.createPendingUser(email, PASSWORD);
    var requestBody = requestBody("PENDING-RESEND.INTEGRATION@EXAMPLE.COM");

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "resend-pending-user")
        .body(requestBody)
        .when()
        .post(RESEND_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

    // Assert
    OutboxEventEntity outboxEvent = outboxEventJpaRepository.findAll().getFirst();
    var payload = objectMapper.readTree(outboxEvent.getPayload());
    Long cooldownTtl = stringRedisTemplate.getExpire(cooldownKey(email));

    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEvent.getAggregateType()).isEqualTo(IamOutboxEventTypes.AGGREGATE_USER);
    assertThat(outboxEvent.getAggregateId()).isEqualTo(user.getId().toString());
    assertThat(outboxEvent.getEventType())
        .isEqualTo(IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED);
    assertThat(outboxEvent.getSchemaVersion()).isEqualTo(1);
    assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(outboxEvent.getAttempts()).isZero();
    assertThat(outboxEvent.getPublishedAt()).isNull();
    assertThat(payload.path("userId").asString()).isEqualTo(user.getId().toString());
    assertThat(payload.path("email").asString()).isEqualTo(email);
    assertThat(payload.path("reason").asString()).isEqualTo(EmailDispatchReason.RESEND.name());
    assertThat(payload.has("otp")).isFalse();
    assertThat(cooldownTtl)
        .isPositive()
        .isLessThanOrEqualTo(emailVerificationPolicy.resendCooldown().toSeconds());
  }

  @Test
  @DisplayName("Deve retornar 200 sem revelar que o e-mail não está cadastrado")
  void shouldReturnGenericResponseWithoutSideEffectsForUnknownEmail() {
    // Arrange
    String email = "unknown-resend.integration@example.com";
    var requestBody = requestBody(email);

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "resend-unknown-email")
        .body(requestBody)
        .when()
        .post(RESEND_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

    // Assert
    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(stringRedisTemplate.hasKey(cooldownKey(email))).isFalse();
  }

  @Test
  @DisplayName("Deve retornar 200 sem publicar evento para usuário já ativo")
  void shouldReturnGenericResponseWithoutSideEffectsForActiveUser() {
    // Arrange
    String email = "active-resend.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    var requestBody = requestBody(email);

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "resend-active-user")
        .body(requestBody)
        .when()
        .post(RESEND_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

    // Assert
    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(stringRedisTemplate.hasKey(cooldownKey(email))).isFalse();
  }

  @Test
  @DisplayName("Deve retornar 429 e reverter o novo evento durante o cooldown")
  void shouldRejectRepeatedResendDuringCooldownWithoutDuplicatingOutboxEvent() {
    // Arrange
    String email = "cooldown-resend.integration@example.com";
    userTestDataFactory.createPendingUser(email, PASSWORD);
    var requestBody = requestBody(email);

    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "resend-before-cooldown")
        .body(requestBody)
        .when()
        .post(RESEND_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200);

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "resend-during-cooldown")
        .body(requestBody)
        .when()
        .post(RESEND_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(429)
        .contentType("application/problem+json")
        .header(
            HttpHeaders.RETRY_AFTER,
            String.valueOf(emailVerificationPolicy.resendCooldown().toSeconds()))
        .body("title", is("Muitas requisições"))
        .body("type", is(ProblemType.TOO_MANY_REQUESTS))
        .body("detail", is(CommonErrorCode.TOO_MANY_REQUESTS.getMessage()))
        .body("errorCode", is(CommonErrorCode.TOO_MANY_REQUESTS.getCode()));

    // Assert
    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
    assertThat(stringRedisTemplate.hasKey(cooldownKey(email))).isTrue();
  }

  @Test
  @DisplayName("Deve retornar 429 após exceder o limite de reenvios por e-mail")
  void shouldRateLimitResendAttemptsByEmail() {
    // Arrange
    String email = "rate-limit-resend.integration@example.com";
    var requestBody = requestBody(email);

    for (int attempt = 1; attempt <= 3; attempt++) {
      given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", "resend-rate-limit-" + attempt)
          .body(requestBody)
          .when()
          .post(RESEND_ENDPOINT)
          .then()
          .log()
          .ifValidationFails()
          .statusCode(200);
    }

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "resend-rate-limit-blocked")
        .body(requestBody)
        .when()
        .post(RESEND_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(429)
        .contentType("application/problem+json")
        .header(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+"))
        .body("title", is("Muitas requisições"))
        .body("type", is(ProblemType.TOO_MANY_REQUESTS))
        .body("detail", is(CommonErrorCode.TOO_MANY_REQUESTS.getMessage()))
        .body("errorCode", is(CommonErrorCode.TOO_MANY_REQUESTS.getCode()));

    // Assert
    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(stringRedisTemplate.hasKey(cooldownKey(email))).isFalse();
  }

  @Test
  @DisplayName("Deve aceitar apenas um reenvio concorrente e reverter o evento rejeitado")
  void shouldAcceptOnlyOneConcurrentResendAndRollbackRejectedEvent() throws Exception {
    // Arrange
    String email = "concurrent-resend.integration@example.com";
    UserEntity user = userTestDataFactory.createPendingUser(email, PASSWORD);
    var requestBody = requestBody(email);
    var workersReady = new CountDownLatch(2);
    var startSignal = new CountDownLatch(1);

    // Act
    List<ResendHttpResult> results;
    try (var executor = Executors.newFixedThreadPool(2)) {
      var firstRequest =
          executor.submit(
              () ->
                  resendAfterSignal(
                      requestBody,
                      "concurrent-resend-first",
                      workersReady,
                      startSignal));
      var secondRequest =
          executor.submit(
              () ->
                  resendAfterSignal(
                      requestBody,
                      "concurrent-resend-second",
                      workersReady,
                      startSignal));

      if (!workersReady.await(5, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Concurrent resend requests did not become ready");
      }
      startSignal.countDown();
      results =
          List.of(
              firstRequest.get(10, TimeUnit.SECONDS),
              secondRequest.get(10, TimeUnit.SECONDS));
    }

    // Assert
    OutboxEventEntity outboxEvent = outboxEventJpaRepository.findAll().getFirst();
    var payload = objectMapper.readTree(outboxEvent.getPayload());
    Long cooldownTtl = stringRedisTemplate.getExpire(cooldownKey(email));

    assertThat(results).extracting(ResendHttpResult::statusCode).containsExactlyInAnyOrder(200, 429);
    assertThat(results)
        .filteredOn(result -> result.statusCode() == 429)
        .singleElement()
        .satisfies(
            result -> {
              assertThat(result.errorCode()).isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS.getCode());
              assertThat(result.retryAfter())
                  .isEqualTo(String.valueOf(emailVerificationPolicy.resendCooldown().toSeconds()));
            });
    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEvent.getAggregateId()).isEqualTo(user.getId().toString());
    assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(payload.path("reason").asString()).isEqualTo(EmailDispatchReason.RESEND.name());
    assertThat(cooldownTtl)
        .isPositive()
        .isLessThanOrEqualTo(emailVerificationPolicy.resendCooldown().toSeconds());
  }

  private String requestBody(String email) {
    return """
        {
          "email": "%s"
        }
        """
        .formatted(email);
  }

  private String cooldownKey(String email) {
    return COOLDOWN_KEY_PREFIX + email;
  }

  private ResendHttpResult resendAfterSignal(
      String requestBody,
      String idempotencyKey,
      CountDownLatch workersReady,
      CountDownLatch startSignal)
      throws InterruptedException {
    workersReady.countDown();
    if (!startSignal.await(5, TimeUnit.SECONDS)) {
      throw new IllegalStateException("Concurrent resend start signal was not received");
    }

    var response =
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", idempotencyKey)
            .body(requestBody)
            .when()
            .post(RESEND_ENDPOINT);
    return new ResendHttpResult(
        response.statusCode(),
        response.jsonPath().getString("errorCode"),
        response.header(HttpHeaders.RETRY_AFTER));
  }

  private record ResendHttpResult(int statusCode, String errorCode, String retryAfter) {}
}
