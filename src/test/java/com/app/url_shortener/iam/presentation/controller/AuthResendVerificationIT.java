package com.app.url_shortener.iam.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.ConcurrentTestExecutor;
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
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import tools.jackson.databind.ObjectMapper;

@DisplayName("Testes de Integração - Endpoint de reenvio de verificação")
class AuthResendVerificationIT extends AbstractIntegrationTest {

  private static final String RESEND_ENDPOINT = "/api/v1/auth/resend-verification";
  private static final String PASSWORD = "secure-password";
  private static final String SUCCESS_MESSAGE =
      "Enviamos um novo código de verificação para o seu e-mail.";
  private static final String COOLDOWN_KEY_PREFIX = "auth:email-verification:resend-cooldown:";

  private final UserTestDataFactory userTestDataFactory;
  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final StringRedisTemplate stringRedisTemplate;
  private final EmailVerificationPolicy emailVerificationPolicy;
  private final ObjectMapper objectMapper;

  @Autowired
  AuthResendVerificationIT(
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
    Response response = resend(requestBody, "resend-pending-user");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

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
  @DisplayName("Deve retornar 400 para payload inválido sem persistir evento ou cooldown")
  void shouldRejectInvalidRequestWithoutPersistingState() {
    // Arrange
    var requestBody =
        """
        {
          "email": "invalid-email"
        }
        """;

    // Act
    Response response = resend(requestBody, "resend-invalid-payload");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage()))
        .body("errorCode", is(CommonErrorCode.REQUEST_VALIDATION_FAILED.getCode()))
        .body("errors.field", hasItems("email"));

    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(stringRedisTemplate.hasKey(cooldownKey("invalid-email"))).isFalse();
  }

  @Test
  @DisplayName(
      "Deve retornar 400 quando a chave de idempotência estiver ausente sem processar reenvio")
  void shouldRequireIdempotencyKeyBeforeProcessingResendVerification() {
    // Arrange
    String email = "missing-idempotency-resend.integration@example.com";
    userTestDataFactory.createPendingUser(email, PASSWORD);
    var requestBody = requestBody(email);

    // Act
    Response response = resendWithoutIdempotencyKey(requestBody);

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(CommonErrorCode.IDEMPOTENCY_HEADER_MISSING.getMessage()))
        .body("errorCode", is(CommonErrorCode.IDEMPOTENCY_HEADER_MISSING.getCode()));

    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(stringRedisTemplate.hasKey(cooldownKey(email))).isFalse();
  }

  @Test
  @DisplayName("Deve reutilizar resposta para mesma chave e payload sem duplicar evento")
  void shouldReplayCompletedResendForSameIdempotencyKeyAndPayload() {
    // Arrange
    String email = "idempotent-resend.integration@example.com";
    userTestDataFactory.createPendingUser(email, PASSWORD);
    var requestBody = requestBody(email);
    String idempotencyKey = "resend-idempotent-replay";

    // Act
    Response firstResponse = resend(requestBody, idempotencyKey);
    Response replayedResponse = resend(requestBody, idempotencyKey);

    // Assert
    firstResponse
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));
    replayedResponse
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
    assertThat(stringRedisTemplate.hasKey(cooldownKey(email))).isTrue();
  }

  @Test
  @DisplayName("Deve rejeitar mesma chave de idempotência com payload diferente")
  void shouldRejectSameIdempotencyKeyWithDifferentPayload() {
    // Arrange
    String firstEmail = "idempotent-first-resend.integration@example.com";
    String conflictingEmail = "idempotent-conflict-resend.integration@example.com";
    var firstRequestBody = requestBody(firstEmail);
    var conflictingRequestBody = requestBody(conflictingEmail);
    String idempotencyKey = "resend-idempotent-conflict";

    // Act
    Response firstResponse = resend(firstRequestBody, idempotencyKey);
    Response conflictingResponse = resend(conflictingRequestBody, idempotencyKey);

    // Assert
    firstResponse.then().log().ifValidationFails().statusCode(200);
    conflictingResponse
        .then()
        .log()
        .ifValidationFails()
        .statusCode(409)
        .contentType("application/problem+json")
        .body("title", is("Conflito"))
        .body("type", is(ProblemType.CONFLICT))
        .body("detail", is(CommonErrorCode.IDEMPOTENCY_IN_PROCESSING.getMessage()))
        .body("errorCode", is(CommonErrorCode.IDEMPOTENCY_IN_PROCESSING.getCode()));

    assertThat(outboxEventJpaRepository.count()).isZero();
    assertThat(stringRedisTemplate.hasKey(cooldownKey(firstEmail))).isFalse();
    assertThat(stringRedisTemplate.hasKey(cooldownKey(conflictingEmail))).isFalse();
  }

  @Test
  @DisplayName("Deve retornar 200 sem revelar que o e-mail não está cadastrado")
  void shouldReturnGenericResponseWithoutSideEffectsForUnknownEmail() {
    // Arrange
    String email = "unknown-resend.integration@example.com";
    var requestBody = requestBody(email);

    // Act
    Response response = resend(requestBody, "resend-unknown-email");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

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
    Response response = resend(requestBody, "resend-active-user");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

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

    Response firstResponse = resend(requestBody, "resend-before-cooldown");

    // Act
    Response cooldownResponse = resend(requestBody, "resend-during-cooldown");

    // Assert
    firstResponse.then().log().ifValidationFails().statusCode(200);
    cooldownResponse
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
      resend(requestBody, "resend-rate-limit-" + attempt)
          .then()
          .log()
          .ifValidationFails()
          .statusCode(200);
    }

    // Act
    Response response = resend(requestBody, "resend-rate-limit-blocked");

    // Assert
    response
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

    // Act
    List<ResendHttpResult> results =
        ConcurrentTestExecutor.execute(
            2, attempt -> toResendHttpResult(resend(requestBody, "concurrent-resend-" + attempt)));

    // Assert
    OutboxEventEntity outboxEvent = outboxEventJpaRepository.findAll().getFirst();
    var payload = objectMapper.readTree(outboxEvent.getPayload());
    Long cooldownTtl = stringRedisTemplate.getExpire(cooldownKey(email));

    assertThat(results)
        .extracting(ResendHttpResult::statusCode)
        .containsExactlyInAnyOrder(200, 429);
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

  private Response resend(String requestBody, String idempotencyKey) {
    return given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", idempotencyKey)
        .body(requestBody)
        .when()
        .post(RESEND_ENDPOINT);
  }

  private Response resendWithoutIdempotencyKey(String requestBody) {
    return given().contentType(ContentType.JSON).body(requestBody).when().post(RESEND_ENDPOINT);
  }

  private String cooldownKey(String email) {
    return COOLDOWN_KEY_PREFIX + email;
  }

  private ResendHttpResult toResendHttpResult(Response response) {
    return new ResendHttpResult(
        response.statusCode(),
        response.jsonPath().getString("errorCode"),
        response.header(HttpHeaders.RETRY_AFTER));
  }

  private record ResendHttpResult(int statusCode, String errorCode, String retryAfter) {}
}
