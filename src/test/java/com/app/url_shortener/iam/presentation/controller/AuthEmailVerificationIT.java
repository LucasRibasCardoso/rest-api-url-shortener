package com.app.url_shortener.iam.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.ConcurrentTestExecutor;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenRepositoryPort;
import com.app.url_shortener.iam.application.port.output.VerificationCodeProtectorPort;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.domain.model.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import com.app.url_shortener.iam.infrastructure.entity.EmailVerificationTokenEntity;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.repository.EmailVerificationTokenJpaRepository;
import com.app.url_shortener.iam.infrastructure.repository.UserJpaRepository;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;

@DisplayName("Testes de Integração - Endpoint de verificação de e-mail")
class AuthEmailVerificationIT extends AbstractIntegrationTest {

  private static final String VERIFY_EMAIL_ENDPOINT = "/api/v1/auth/verify-email";
  private static final String SUCCESS_MESSAGE =
      "E-mail verificado com sucesso. Agora você pode fazer login na sua conta.";
  private static final String VALID_CODE = "123456";
  private static final String INVALID_CODE = "654321";
  private static final String PASSWORD = "secure-password";

  private final UserTestDataFactory userTestDataFactory;
  private final VerificationCodeProtectorPort verificationCodeProtectorPort;
  private final EmailVerificationTokenRepositoryPort emailVerificationTokenRepositoryPort;
  private final UserJpaRepository userJpaRepository;
  private final EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository;
  private final JdbcTemplate jdbcTemplate;

  @Autowired
  AuthEmailVerificationIT(
      UserTestDataFactory userTestDataFactory,
      VerificationCodeProtectorPort verificationCodeProtectorPort,
      EmailVerificationTokenRepositoryPort emailVerificationTokenRepositoryPort,
      UserJpaRepository userJpaRepository,
      EmailVerificationTokenJpaRepository emailVerificationTokenJpaRepository,
      JdbcTemplate jdbcTemplate) {
    this.userTestDataFactory = userTestDataFactory;
    this.verificationCodeProtectorPort = verificationCodeProtectorPort;
    this.emailVerificationTokenRepositoryPort = emailVerificationTokenRepositoryPort;
    this.userJpaRepository = userJpaRepository;
    this.emailVerificationTokenJpaRepository = emailVerificationTokenJpaRepository;
    this.jdbcTemplate = jdbcTemplate;
  }

  @Test
  @DisplayName("Deve retornar 200 e ativar a conta ao verificar e-mail com código válido")
  void shouldActivateAccountAndConsumeTokenWithValidCode() {
    // Arrange
    String email = "valid-verification.integration@example.com";
    UserEntity user = userTestDataFactory.createPendingUser(email, PASSWORD);
    EmailVerificationToken token = createToken(user, Instant.now().plus(Duration.ofMinutes(10)));
    var requestBody =
        """
        {
          "email": "VALID-VERIFICATION.INTEGRATION@EXAMPLE.COM",
          "code": "123456"
        }
        """;

    // Act
    Response response = verifyEmail(requestBody, "verify-email-valid-code");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));

    UserEntity savedUser = userJpaRepository.findByEmailWithRoles(email).orElseThrow();
    EmailVerificationTokenEntity savedToken =
        emailVerificationTokenJpaRepository.findById(token.getId()).orElseThrow();

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(savedUser.isEmailVerified()).isTrue();
    assertThat(savedUser.getRoles())
        .singleElement()
        .satisfies(
            role -> {
              assertThat(role.getName()).isEqualTo("USER");
              assertThat(role.isDefault()).isTrue();
            });
    assertThat(savedToken.getConsumedAt()).isNotNull();
    assertThat(savedToken.getRevokedAt()).isNull();
    assertThat(savedToken.getFailedAttempts()).isZero();
  }

  @Test
  @DisplayName("Deve retornar 400 e registrar tentativa falha quando o código estiver incorreto")
  void shouldRegisterFailedAttemptWithoutActivatingAccountWhenCodeIsInvalid() {
    // Arrange
    String email = "invalid-code.integration@example.com";
    UserEntity user = userTestDataFactory.createPendingUser(email, PASSWORD);
    EmailVerificationToken token = createToken(user, Instant.now().plus(Duration.ofMinutes(10)));
    var requestBody = invalidCodeRequestBody(email);

    // Act
    Response response = verifyEmail(requestBody, "verify-email-invalid-code");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE.getCode()));

    UserEntity savedUser = userJpaRepository.findByEmailWithRoles(email).orElseThrow();
    EmailVerificationTokenEntity savedToken =
        emailVerificationTokenJpaRepository.findById(token.getId()).orElseThrow();

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    assertThat(savedUser.isEmailVerified()).isFalse();
    assertThat(savedUser.getRoles()).isEmpty();
    assertThat(savedToken.getConsumedAt()).isNull();
    assertThat(savedToken.getRevokedAt()).isNull();
    assertThat(savedToken.getFailedAttempts()).isEqualTo(1);
    assertThat(savedToken.getLastAttemptAt()).isNotNull();
  }

  @Test
  @DisplayName("Deve retornar 400 sem alterar a conta quando o código estiver expirado")
  void shouldRejectExpiredCodeWithoutChangingPersistedState() {
    // Arrange
    String email = "expired-code.integration@example.com";
    UserEntity user = userTestDataFactory.createPendingUser(email, PASSWORD);
    EmailVerificationToken token = createToken(user, Instant.now().plus(Duration.ofMinutes(10)));
    expireToken(token);
    var requestBody =
        """
        {
          "email": "expired-code.integration@example.com",
          "code": "123456"
        }
        """;

    // Act
    Response response = verifyEmail(requestBody, "verify-email-expired-code");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE.getCode()));

    UserEntity savedUser = userJpaRepository.findByEmailWithRoles(email).orElseThrow();
    EmailVerificationTokenEntity savedToken =
        emailVerificationTokenJpaRepository.findById(token.getId()).orElseThrow();

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    assertThat(savedUser.isEmailVerified()).isFalse();
    assertThat(savedUser.getRoles()).isEmpty();
    assertThat(savedToken.getConsumedAt()).isNull();
    assertThat(savedToken.getRevokedAt()).isNull();
    assertThat(savedToken.getFailedAttempts()).isZero();
    assertThat(savedToken.getLastAttemptAt()).isNull();
  }

  @Test
  @DisplayName("Deve retornar 429 após exceder o limite de verificações por e-mail")
  void shouldRateLimitVerificationAttemptsByEmail() {
    // Arrange
    String email = "rate-limit-verification.integration@example.com";
    UserEntity user = userTestDataFactory.createPendingUser(email, PASSWORD);
    EmailVerificationToken token = createToken(user, Instant.now().plus(Duration.ofMinutes(10)));
    var requestBody = invalidCodeRequestBody(email);

    for (int attempt = 1; attempt <= 5; attempt++) {
      verifyEmail(requestBody, "verify-email-rate-limit-" + attempt)
          .then()
          .log()
          .ifValidationFails()
          .statusCode(400);
    }

    // Act
    Response response = verifyEmail(requestBody, "verify-email-rate-limit-blocked");

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

    UserEntity savedUser = userJpaRepository.findByEmailWithRoles(email).orElseThrow();
    EmailVerificationTokenEntity savedToken =
        emailVerificationTokenJpaRepository.findById(token.getId()).orElseThrow();

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.PENDING_EMAIL_VERIFICATION);
    assertThat(savedUser.isEmailVerified()).isFalse();
    assertThat(savedToken.getConsumedAt()).isNull();
    assertThat(savedToken.getFailedAttempts()).isEqualTo(5);
  }

  @Test
  @DisplayName("Deve rejeitar reutilização sequencial de OTP já consumido")
  void shouldRejectSequentialReuseOfConsumedOtp() {
    // Arrange
    String email = "reused-consumed-otp.integration@example.com";
    UserEntity user = userTestDataFactory.createPendingUser(email, PASSWORD);
    EmailVerificationToken token = createToken(user, Instant.now().plus(Duration.ofMinutes(10)));
    var requestBody =
        """
        {
          "email": "reused-consumed-otp.integration@example.com",
          "code": "123456"
        }
        """;

    Response firstResponse = verifyEmail(requestBody, "verify-email-reuse-first");

    // Act
    Response secondResponse = verifyEmail(requestBody, "verify-email-reuse-second");

    // Assert
    firstResponse
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("message", is(SUCCESS_MESSAGE));
    secondResponse
        .then()
        .log()
        .ifValidationFails()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE.getCode()));

    UserEntity savedUser = userJpaRepository.findByEmailWithRoles(email).orElseThrow();
    EmailVerificationTokenEntity savedToken =
        emailVerificationTokenJpaRepository.findById(token.getId()).orElseThrow();

    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(savedUser.isEmailVerified()).isTrue();
    assertThat(savedUser.getRoles())
        .singleElement()
        .satisfies(role -> assertThat(role.getName()).isEqualTo("USER"));
    assertThat(savedToken.getConsumedAt()).isNotNull();
    assertThat(savedToken.getRevokedAt()).isNull();
    assertThat(savedToken.getFailedAttempts()).isZero();
  }

  @Test
  @DisplayName("Deve consumir o OTP apenas uma vez em verificações concorrentes")
  void shouldConsumeOtpOnlyOnceForConcurrentVerificationRequests() throws Exception {
    // Arrange
    String email = "concurrent-verification.integration@example.com";
    UserEntity user = userTestDataFactory.createPendingUser(email, PASSWORD);
    EmailVerificationToken token = createToken(user, Instant.now().plus(Duration.ofMinutes(10)));
    var requestBody =
        """
        {
          "email": "concurrent-verification.integration@example.com",
          "code": "123456"
        }
        """;

    // Act
    List<VerificationHttpResult> results =
        ConcurrentTestExecutor.execute(
            2,
            attempt ->
                toVerificationHttpResult(
                    verifyEmail(requestBody, "verify-email-concurrent-" + attempt)));

    // Assert
    UserEntity savedUser = userJpaRepository.findByEmailWithRoles(email).orElseThrow();
    EmailVerificationTokenEntity savedToken =
        emailVerificationTokenJpaRepository.findById(token.getId()).orElseThrow();

    assertThat(results)
        .extracting(VerificationHttpResult::statusCode)
        .containsExactlyInAnyOrder(200, 400);
    assertThat(results)
        .filteredOn(result -> result.statusCode() == 400)
        .singleElement()
        .extracting(VerificationHttpResult::errorCode)
        .isEqualTo(IamErrorCode.AUTH_INVALID_OR_EXPIRED_VERIFICATION_CODE.getCode());
    assertThat(savedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    assertThat(savedUser.isEmailVerified()).isTrue();
    assertThat(savedUser.getRoles())
        .singleElement()
        .satisfies(role -> assertThat(role.getName()).isEqualTo("USER"));
    assertThat(savedToken.getConsumedAt()).isNotNull();
    assertThat(savedToken.getRevokedAt()).isNull();
    assertThat(savedToken.getFailedAttempts()).isZero();
  }

  private EmailVerificationToken createToken(UserEntity user, Instant expiresAt) {
    VerificationCode code = VerificationCode.of(VALID_CODE);
    Instant issuedAt = Instant.now();
    EmailVerificationToken token =
        EmailVerificationToken.create(
            user.getId(),
            user.getEmail(),
            verificationCodeProtectorPort.hash(code),
            verificationCodeProtectorPort.encrypt(code),
            expiresAt,
            issuedAt);
    return emailVerificationTokenRepositoryPort.save(token);
  }

  private void expireToken(EmailVerificationToken token) {
    Instant now = Instant.now();
    jdbcTemplate.update(
        """
        UPDATE email_verification_tokens
           SET created_at = ?,
               expires_at = ?
         WHERE id = ?
        """,
        Timestamp.from(now.minus(Duration.ofMinutes(11))),
        Timestamp.from(now.minus(Duration.ofMinutes(1))),
        token.getId());
  }

  private String invalidCodeRequestBody(String email) {
    return """
        {
          "email": "%s",
          "code": "%s"
        }
        """
        .formatted(email, INVALID_CODE);
  }

  private Response verifyEmail(String requestBody, String idempotencyKey) {
    return given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", idempotencyKey)
        .body(requestBody)
        .when()
        .post(VERIFY_EMAIL_ENDPOINT);
  }

  private VerificationHttpResult toVerificationHttpResult(Response response) {
    return new VerificationHttpResult(
        response.statusCode(), response.jsonPath().getString("errorCode"));
  }

  private record VerificationHttpResult(int statusCode, String errorCode) {}
}
