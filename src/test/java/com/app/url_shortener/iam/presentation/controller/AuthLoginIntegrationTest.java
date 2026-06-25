package com.app.url_shortener.iam.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.repository.RefreshTokenJpaRepository;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import io.restassured.http.ContentType;
import java.net.HttpCookie;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

@DisplayName("Testes de Integração - Endpoint de login")
class AuthLoginIntegrationTest extends AbstractIntegrationTest {

  private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";
  private static final String PASSWORD = "secure-password";
  private static final String CLIENT_IP = "203.0.113.10";

  private final UserTestDataFactory userTestDataFactory;
  private final RefreshTokenJpaRepository refreshTokenJpaRepository;
  private final SecureTokenGeneratorPort secureTokenGeneratorPort;
  private final JwtDecoder jwtDecoder;

  @Autowired
  AuthLoginIntegrationTest(
      UserTestDataFactory userTestDataFactory,
      RefreshTokenJpaRepository refreshTokenJpaRepository,
      SecureTokenGeneratorPort secureTokenGeneratorPort,
      JwtDecoder jwtDecoder) {
    this.userTestDataFactory = userTestDataFactory;
    this.refreshTokenJpaRepository = refreshTokenJpaRepository;
    this.secureTokenGeneratorPort = secureTokenGeneratorPort;
    this.jwtDecoder = jwtDecoder;
  }

  @Test
  @DisplayName("Deve retornar 200, emitir tokens e persistir o hash do refresh token")
  void shouldIssueTokensAndPersistRefreshTokenHashForValidCredentials() {
    // Arrange
    String email = "active-login.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    var requestBody =
        """
        {
          "email": "ACTIVE-LOGIN.INTEGRATION@EXAMPLE.COM",
          "password": "secure-password"
        }
        """;

    // Act
    var response =
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", "login-active-user")
            .header("X-Forwarded-For", CLIENT_IP)
            .body(requestBody)
            .when()
            .post(LOGIN_ENDPOINT)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .header(HttpHeaders.SET_COOKIE, not(blankOrNullString()))
            .body("accessToken", not(blankOrNullString()))
            .body("tokenType", is("Bearer"))
            .body("expiresInSeconds", is(900))
            .body("user.id", is(user.getId().toString()))
            .body("user.name", is(user.getName()))
            .body("user.email", is(email))
            .body("user.plan", is(PlanType.FREE.name()))
            .body("user.roles", contains("USER"))
            .extract()
            .response();

    // Assert
    String accessToken = response.path("accessToken");
    String rawRefreshToken = response.cookie("refreshToken");
    String setCookieHeader = response.header(HttpHeaders.SET_COOKIE);
    HttpCookie refreshCookie = HttpCookie.parse(setCookieHeader).getFirst();
    var cookieAttributes =
        Arrays.stream(setCookieHeader.split(";"))
            .skip(1)
            .map(String::trim)
            .toList();

    assertThat(rawRefreshToken).isNotBlank();
    assertThat(refreshCookie.getName()).isEqualTo("refreshToken");
    assertThat(refreshCookie.isHttpOnly()).isTrue();
    assertThat(refreshCookie.getSecure()).isTrue();
    assertThat(refreshCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(refreshCookie.getMaxAge()).isEqualTo(Duration.ofDays(7).toSeconds());
    assertThat(cookieAttributes).contains("SameSite=Strict");

    Jwt jwt = jwtDecoder.decode(accessToken);
    assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
    assertThat(jwt.getClaimAsString("plan")).isEqualTo(PlanType.FREE.name());
    assertThat(jwt.getClaimAsStringList("authorities")).contains("ROLE_USER", "url:create", "url:read:own");
    assertThat(jwt.getIssuedAt()).isNotNull();
    assertThat(jwt.getExpiresAt()).isAfter(Instant.now());

    assertThat(refreshTokenJpaRepository.findAll())
        .singleElement()
        .satisfies(
            refreshToken -> {
              assertThat(refreshToken.getUser().getId()).isEqualTo(user.getId());
              assertThat(refreshToken.getTokenHash()).isEqualTo(secureTokenGeneratorPort.hashToken(rawRefreshToken));
              assertThat(refreshToken.getRevokedAt()).isNull();
              assertThat(refreshToken.getExpiresAt()).isAfter(refreshToken.getCreatedAt());
            });
  }

  @Test
  @DisplayName("Deve retornar 400 sem emitir tokens quando a senha estiver incorreta")
  void shouldRejectInvalidPasswordWithoutPersistingRefreshToken() {
    // Arrange
    String email = "invalid-password.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    var requestBody =
        """
        {
          "email": "invalid-password.integration@example.com",
          "password": "wrong-password"
        }
        """;

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "login-invalid-password")
        .header("X-Forwarded-For", CLIENT_IP)
        .body(requestBody)
        .when()
        .post(LOGIN_ENDPOINT)
        .then()
        .statusCode(400)
        .contentType("application/problem+json")
        .header(HttpHeaders.SET_COOKIE, blankOrNullString())
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(IamErrorCode.AUTH_INVALID_CREDENTIALS.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_INVALID_CREDENTIALS.getCode()));

    // Assert
    assertThat(refreshTokenJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName("Deve retornar 403 sem emitir tokens para conta pendente de verificação")
  void shouldRejectPendingAccountWithoutPersistingRefreshToken() {
    // Arrange
    String email = "pending-login.integration@example.com";
    userTestDataFactory.createPendingUser(email, PASSWORD);
    var requestBody =
        """
        {
          "email": "pending-login.integration@example.com",
          "password": "secure-password"
        }
        """;

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "login-pending-user")
        .header("X-Forwarded-For", CLIENT_IP)
        .body(requestBody)
        .when()
        .post(LOGIN_ENDPOINT)
        .then()
        .statusCode(403)
        .contentType("application/problem+json")
        .header(HttpHeaders.SET_COOKIE, blankOrNullString())
        .body("title", is("Proibido"))
        .body("type", is(ProblemType.FORBIDDEN))
        .body("detail", is(IamErrorCode.AUTH_ACCOUNT_PENDING_VERIFICATION.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_ACCOUNT_PENDING_VERIFICATION.getCode()));

    // Assert
    assertThat(refreshTokenJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName("Deve retornar 429 sem autenticar após exceder o limite de login por e-mail e IP")
  void shouldRateLimitLoginAttemptsByEmailAndClientIp() {
    // Arrange
    String email = "rate-limit-login.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    var requestBody =
        """
        {
          "email": "rate-limit-login.integration@example.com",
          "password": "wrong-password"
        }
        """;

    for (int attempt = 1; attempt <= 5; attempt++) {
      given()
          .contentType(ContentType.JSON)
          .header("Idempotency-Key", "login-rate-limit-" + attempt)
          .header("X-Forwarded-For", CLIENT_IP)
          .body(requestBody)
          .when()
          .post(LOGIN_ENDPOINT)
          .then()
          .statusCode(400);
    }

    // Act
    given()
        .contentType(ContentType.JSON)
        .header("Idempotency-Key", "login-rate-limit-blocked")
        .header("X-Forwarded-For", CLIENT_IP)
        .body(requestBody)
        .when()
        .post(LOGIN_ENDPOINT)
        .then()
        .statusCode(429)
        .contentType("application/problem+json")
        .header(HttpHeaders.SET_COOKIE, blankOrNullString())
        .header(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+"))
        .body("title", is("Muitas requisições"))
        .body("type", is(ProblemType.TOO_MANY_REQUESTS))
        .body("detail", is(CommonErrorCode.TOO_MANY_REQUESTS.getMessage()))
        .body("errorCode", is(CommonErrorCode.TOO_MANY_REQUESTS.getCode()));

    // Assert
    assertThat(refreshTokenJpaRepository.count()).isZero();
  }

}
