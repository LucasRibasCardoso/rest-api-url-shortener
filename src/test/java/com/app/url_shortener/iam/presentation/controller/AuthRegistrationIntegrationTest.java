package com.app.url_shortener.iam.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

import com.app.url_shortener.config.AbstractIntegrationTest;
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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.ObjectMapper;

class AuthRegistrationIntegrationTest extends AbstractIntegrationTest {

  private static final String REGISTER_ENDPOINT = "/api/v1/auth/register";
  private static final String SUCCESS_MESSAGE = "Enviamos um código de verificação para o seu e-mail.";

  private final UserJpaRepository userJpaRepository;
  private final OutboxEventJpaRepository outboxEventJpaRepository;
  private final PasswordEncoder passwordEncoder;
  private final ObjectMapper objectMapper;

  @Autowired
  AuthRegistrationIntegrationTest(
      UserJpaRepository userJpaRepository,
      OutboxEventJpaRepository outboxEventJpaRepository,
      PasswordEncoder passwordEncoder,
      ObjectMapper objectMapper) {
    this.userJpaRepository = userJpaRepository;
    this.outboxEventJpaRepository = outboxEventJpaRepository;
    this.passwordEncoder = passwordEncoder;
    this.objectMapper = objectMapper;
  }

  @Test
  @DisplayName("Deve retornar 201 ao cadastrar usuário anônimo e persistir evento de verificação na outbox")
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
    UserEntity savedUser = userJpaRepository.findByEmail("maria.integration@example.com").orElseThrow();

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
    assertThat(outboxEvent.getEventType()).isEqualTo(IamOutboxEventTypes.EMAIL_VERIFICATION_REQUESTED);
    assertThat(outboxEvent.getSchemaVersion()).isEqualTo(1);
    assertThat(outboxEvent.getStatus()).isEqualTo(OutboxEventStatus.PENDING);
    assertThat(outboxEvent.getAttempts()).isZero();
    assertThat(outboxEvent.getLastError()).isNull();
    assertThat(outboxEvent.getPublishedAt()).isNull();

    var payload = objectMapper.readTree(outboxEvent.getPayload());
    assertThat(payload.path("userId").asString()).isEqualTo(savedUser.getId().toString());
    assertThat(payload.path("email").asString()).isEqualTo("maria.integration@example.com");
    assertThat(payload.path("reason").asString()).isEqualTo(EmailDispatchReason.REGISTER.name());
    assertThat(payload.has("otp")).isFalse();
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
  @DisplayName("Deve retornar 400 quando a chave de idempotência estiver ausente sem processar o cadastro")
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
}
