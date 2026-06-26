package com.app.url_shortener.iam.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.ConcurrentTestExecutor;
import com.app.url_shortener.iam.application.event.IamOutboxEventTypes;
import com.app.url_shortener.iam.domain.enums.EmailDispatchReason;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.repository.UserJpaRepository;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.outbox.domain.model.OutboxEventStatus;
import com.app.url_shortener.shared.outbox.infrastructure.entity.OutboxEventEntity;
import com.app.url_shortener.shared.outbox.infrastructure.repository.OutboxEventJpaRepository;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

@TestPropertySource(
    properties = {
      "app.rate-limit.policies.auth-register-email.capacity=3",
      "app.rate-limit.policies.auth-register-email.refill-tokens=3",
      "app.rate-limit.policies.auth-register-ip.capacity=4",
      "app.rate-limit.policies.auth-register-ip.refill-tokens=4"
    })
@DisplayName("Testes de Integração - Endpoint de cadastro")
class AuthRegistrationIntegrationTest extends AbstractIntegrationTest {

  private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
  private static final String SUCCESS_MESSAGE =
      "Enviamos um código de verificação para o seu e-mail.";
  private static final String ROLLBACK_TEST_EMAIL = "outbox-rollback.integration@example.com";
  private static final String OUTBOX_FAILURE_TRIGGER = "trg_reject_register_outbox_for_test";
  private static final String OUTBOX_FAILURE_FUNCTION = "reject_register_outbox_for_test";

  private final UserJpaRepository userJpaRepository;
  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  AuthRegistrationIntegrationTest(
      UserJpaRepository userJpaRepository,
      OutboxEventJpaRepository outboxEventJpaRepository,
      PasswordEncoder passwordEncoder,
      ObjectMapper objectMapper,
      JdbcTemplate jdbcTemplate) {
    this.userJpaRepository = userJpaRepository;
    this.outboxEventJpaRepository = outboxEventJpaRepository;
    this.passwordEncoder = passwordEncoder;
    this.objectMapper = objectMapper;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Test
  @DisplayName(
      "Deve retornar 201 ao cadastrar usuário anônimo e persistir evento de verificação na outbox")
  void shouldRegisterAnonymousUserAndPersistVerificationOutboxEvent() {
    // Arrange
    var requestBody =
        """
        {
          "name": "Maria da Silva",
          "email": "maria.integration@example.com",
          "password": "secure-password"
        }
        """;

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "register-maria-integration")
        .body(requestBody)
        .when()
        .post(REGISTER_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(201)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

    // Assert
    UserEntity savedUser =
        userJpaRepository.findByEmail("maria.integration@example.com").orElseThrow();

    assertThat(savedUser.getName()).isEqualTo("Maria da Silva");
    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    assertThat(savedUser.getPlan()).isEqualTo(PlanType.FREE);
    assertThat(savedUser.isEmailVerified()).isFalse();
    assertThat(savedUser.getPasswordHash()).isNotEqualTo("secure-password");
    assertThat(passwordEncoder.matches("secure-password", savedUser.getPasswordHash())).isTrue();

    List<OutboxEventEntity> outboxEvents = outboxEventJpaRepository.findAll();
    assertThat(outboxEvents).hasSize(1);

    OutboxEventEntity outboxEvent = outboxEvents.getFirst();
    assertThat(outboxEvent.getAggregateType()).isEqualTo(IamOutboxEventTypes.AGGREGATE_USER);
    assertThat(outboxEvent.getAggregateId()).isEqualTo(savedUser.getId().toString());
    assertThat(outboxEvent.getEventType())
        .isEqualTo(IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED);
    assertThat(outboxEvent.getSchemaVersion()).isEqualTo(1);
    assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(outboxEvent.getAttempts()).isZero();
    assertThat(outboxEvent.getLastError()).isNull();
    assertThat(outboxEvent.getPublishedAt()).isNull();

    var payload = objectMapper.readTree(outboxEvent.getPayload());
    assertThat(payload.path("userId").asString()).isEqualTo(savedUser.getId().toString());
    assertThat(payload.path("email").asString()).isEqualTo("maria.integration@example.com");
    assertThat(payload.path("reason").asString()).isEqualTo(EmailDispatchReason.REGISTER.name());
  }

  @Test
  @DisplayName("Deve retornar 400 para cadastro inválido sem persistir usuário ou evento de outbox")
  void shouldRejectInvalidRegistrationWithoutPersistingState() {
    // Arrange
    var requestBody =
        """
        {
          "name": "A",
          "email": "invalid-email",
          "password": "123"
        }
        """;

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "register-invalid-payload")
        .body(requestBody)
        .when()
        .post(REGISTER_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage()))
        .body("errorCode", is(CommonErrorCode.REQUEST_VALIDATION_FAILED.getCode()))
        .body("errors.field", hasItems("name", "email", "password"));

    // Assert
    assertThat(userJpaRepository.count()).isZero();
    assertThat(outboxEventJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName(
      "Deve retornar 400 quando a chave de idempotência estiver ausente sem processar o cadastro")
  void shouldRequireIdempotencyKeyBeforeProcessingRegistration() {
    // Arrange
    var requestBody =
        """
        {
          "name": "Ana Souza",
          "email": "ana.integration@example.com",
          "password": "secure-password"
        }
        """;

    // Act
    given()
        .contentType(ContentType.JSON)
        .body(requestBody)
        .when()
        .post(REGISTER_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(CommonErrorCode.IDEMPOTENCY_HEADER_MISSING.getMessage()))
        .body("errorCode", is(CommonErrorCode.IDEMPOTENCY_HEADER_MISSING.getCode()));

    // Assert
    assertThat(userJpaRepository.count()).isZero();
    assertThat(outboxEventJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName("Deve reutilizar resposta para mesma chave e payload sem duplicar usuário ou outbox")
  void shouldReplayCompletedRegistrationForSameIdempotencyKeyAndPayload() {
    // Arrange
    var requestBody =
        """
        {
          "name": "Idempotent User",
          "email": "idempotent-replay.integration@example.com",
          "password": "secure-password"
        }
        """;
    String idempotencyKey = "register-idempotent-replay";

    // Act
    Response firstResponse = register(requestBody, idempotencyKey);
    Response replayedResponse = register(requestBody, idempotencyKey);

    // Assert
    firstResponse
        .then()
        .log()
        .ifValidationFails()
        .statusCode(201)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));
    replayedResponse
        .then()
        .log()
        .ifValidationFails()
        .statusCode(201)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

    assertThat(userJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Deve rejeitar mesma chave de idempotência com payload diferente")
  void shouldRejectSameIdempotencyKeyWithDifferentPayload() {
    // Arrange
    var firstRequestBody =
        """
        {
          "name": "First Idempotent User",
          "email": "idempotent-first.integration@example.com",
          "password": "secure-password"
        }
        """;
    var conflictingRequestBody =
        """
        {
          "name": "Conflicting Idempotent User",
          "email": "idempotent-conflict.integration@example.com",
          "password": "another-password"
        }
        """;
    String idempotencyKey = "register-idempotent-conflict";

    // Act
    Response firstResponse = register(firstRequestBody, idempotencyKey);
    Response conflictingResponse = register(conflictingRequestBody, idempotencyKey);

    // Assert
    firstResponse.then().log().ifValidationFails().statusCode(201);
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
    assertThat(userJpaRepository.findByEmail("idempotent-first.integration@example.com"))
        .isPresent();
    assertThat(userJpaRepository.findByEmail("idempotent-conflict.integration@example.com"))
        .isEmpty();
    assertThat(userJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Deve executar apenas um cadastro para mesma chave de idempotência concorrente")
  void shouldExecuteRegistrationOnceForConcurrentSameIdempotencyKey() throws Exception {
    // Arrange
    String email = "concurrent-idempotency.integration@example.com";
    String idempotencyKey = "register-concurrent-idempotency";

    // Act
    List<Response> results =
        ConcurrentTestExecutor.execute(
            2,
            attempt -> register(concurrentUserRequestBody(email), idempotencyKey, "203.0.113.20"));

    // Assert
    assertThat(results).hasSize(2).anyMatch(response -> response.statusCode() == 201);
    assertThat(results)
        .allMatch(response -> response.statusCode() == 201 || response.statusCode() == 409);
    assertThat(results)
        .filteredOn(response -> response.statusCode() == 409)
        .allSatisfy(
            response ->
                assertThat(response.jsonPath().getString("errorCode"))
                    .isEqualTo(CommonErrorCode.IDEMPOTENCY_IN_PROCESSING.getCode()));
    assertThat(userJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Deve retornar 409 para email já cadastrado sem duplicar estado persistente")
  void shouldReturnConflictForRegisteredEmailWithoutDuplicatingPersistedState() {
    // Arrange
    var firstRequestBody =
        """
        {
          "name": "João da Silva",
          "email": "joao.integration@example.com",
          "password": "secure-password"
        }
        """;
    var duplicateRequestBody =
        """
        {
          "name": "Outro Usuário",
          "email": "JOAO.INTEGRATION@example.com",
          "password": "another-password"
        }
        """;

    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "register-joao-first")
        .body(firstRequestBody)
        .when()
        .post(REGISTER_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(201);

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "register-joao-duplicate")
        .body(duplicateRequestBody)
        .when()
        .post(REGISTER_ENDPOINT)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(409)
        .contentType("application/problem+json")
        .body("title", is("Conflito"))
        .body("type", is(ProblemType.CONFLICT))
        .body("detail", is(IamErrorCode.AUTH_EMAIL_ALREADY_EXISTS.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_EMAIL_ALREADY_EXISTS.getCode()));

    // Assert
    assertThat(userJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Deve limitar cadastros concorrentes do mesmo email antes do BCrypt")
  void shouldRateLimitConcurrentRegistrationsForSameEmail() throws Exception {
    // Arrange
    String email = "concurrent-email-rate-limit.integration@example.com";
    String clientIp = "203.0.113.30";

    // Act
    List<Response> results =
        ConcurrentTestExecutor.execute(
            4,
            attempt ->
                register(
                    concurrentUserRequestBody(email),
                    "register-concurrent-email-" + attempt,
                    clientIp));

    // Assert
    assertThat(results).filteredOn(response -> response.statusCode() == 201).hasSize(1);
    assertThat(results).filteredOn(response -> response.statusCode() == 409).hasSize(2);
    assertThat(results)
        .filteredOn(response -> response.statusCode() == 429)
        .singleElement()
        .satisfies(this::assertRateLimited);
    assertThat(userJpaRepository.count()).isEqualTo(1);
    assertThat(outboxEventJpaRepository.count()).isEqualTo(1);
  }

  @Test
  @DisplayName("Deve limitar cadastros concorrentes de um mesmo IP")
  void shouldRateLimitConcurrentRegistrationsFromSameIp() throws Exception {
    // Arrange
    String clientIp = "203.0.113.40";

    // Act
    List<Response> results =
        ConcurrentTestExecutor.execute(
            6,
            attempt ->
                register(
                    concurrentUserRequestBody(
                        "concurrent-ip-" + attempt + ".integration@example.com"),
                    "register-concurrent-ip-" + attempt,
                    clientIp));

    // Assert
    assertThat(results).filteredOn(response -> response.statusCode() == 201).hasSize(4);
    assertThat(results)
        .filteredOn(response -> response.statusCode() == 429)
        .hasSize(2)
        .allSatisfy(this::assertRateLimited);
    assertThat(results)
        .allMatch(response -> response.statusCode() == 201 || response.statusCode() == 429);
    assertThat(userJpaRepository.count()).isEqualTo(4);
    assertThat(outboxEventJpaRepository.count()).isEqualTo(4);
    assertThat(outboxEventJpaRepository.findAll())
        .allSatisfy(
            event ->
                assertThat(userJpaRepository.findById(UUID.fromString(event.getAggregateId())))
                    .isPresent());
  }

  @Test
  @DisplayName("Deve reverter usuário quando a persistência real da outbox falhar")
  void shouldRollbackUserWhenOutboxPersistenceFails() {
    // Arrange
    var requestBody =
        """
        {
          "name": "Rollback User",
          "email": "%s",
          "password": "secure-password"
        }
        """
            .formatted(ROLLBACK_TEST_EMAIL);

    installOutboxFailureTrigger();

    // Act
    Response response;
    try {
      response = register(requestBody, "register-outbox-rollback");
    } finally {
      removeOutboxFailureTrigger();
    }

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(503)
        .contentType("application/problem+json")
        .body("title", is("Infraestrutura"))
        .body("type", is(ProblemType.INFRASTRUCTURE))
        .body("detail", is(CommonErrorCode.DEPENDENCY_FAILURE.getMessage()))
        .body("errorCode", is(CommonErrorCode.DEPENDENCY_FAILURE.getCode()));
    assertThat(userJpaRepository.findByEmail(ROLLBACK_TEST_EMAIL)).isEmpty();
    assertThat(userJpaRepository.count()).isZero();
    assertThat(outboxEventJpaRepository.count()).isZero();
  }

  private Response register(String requestBody, String idempotencyKey) {
    return register(requestBody, idempotencyKey, null);
  }

  private Response register(String requestBody, String idempotencyKey, String clientIp) {
    var request = given().contentType(ContentType.JSON).header("Idempotency-Key", idempotencyKey);

    if (clientIp != null) {
      request.header("X-Forwarded-For", clientIp);
    }

    return given().spec(request).body(requestBody).when().post(REGISTER_ENDPOINT);
  }

  private String concurrentUserRequestBody(String email) {
    return """
        {
          "name": "Concurrent User",
          "email": "%s",
          "password": "secure-password"
        }
        """
        .formatted(email);
  }

  private void installOutboxFailureTrigger() {
    removeOutboxFailureTrigger();
    jdbcTemplate.execute(
        """
        CREATE FUNCTION reject_register_outbox_for_test()
        RETURNS trigger
        LANGUAGE plpgsql
        AS $$
        BEGIN
          IF NEW.event_type = 'EMAIL_VERIFICATION_REQUESTED'
              AND NEW.payload ->> 'email' = 'outbox-rollback.integration@example.com' THEN
            RAISE EXCEPTION 'forced outbox insert failure for integration test';
          END IF;
          RETURN NEW;
        END;
        $$
        """);
    jdbcTemplate.execute(
        """
        CREATE TRIGGER trg_reject_register_outbox_for_test
        BEFORE INSERT ON outbox_events
        FOR EACH ROW
        EXECUTE FUNCTION reject_register_outbox_for_test()
        """);
  }

  private void removeOutboxFailureTrigger() {
    jdbcTemplate.execute("DROP TRIGGER IF EXISTS " + OUTBOX_FAILURE_TRIGGER + " ON outbox_events");
    jdbcTemplate.execute("DROP FUNCTION IF EXISTS " + OUTBOX_FAILURE_FUNCTION + "()");
  }

  private void assertRateLimited(Response response) {
    assertThat(response.jsonPath().getString("errorCode"))
        .isEqualTo(CommonErrorCode.TOO_MANY_REQUESTS.getCode());
    assertThat(response.header(HttpHeaders.RETRY_AFTER)).isNotBlank().matches("\\d+");
    assertThat(response.contentType()).startsWith("application/problem+json");
    assertThat(response.jsonPath().getString("title")).isEqualTo("Muitas requisições");
    assertThat(response.jsonPath().getString("type")).isEqualTo(ProblemType.TOO_MANY_REQUESTS);
  }
}
