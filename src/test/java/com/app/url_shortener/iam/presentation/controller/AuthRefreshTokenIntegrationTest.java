package com.app.url_shortener.iam.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.infrastructure.entity.RefreshTokenEntity;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.repository.RefreshTokenJpaRepository;
import com.app.url_shortener.shared.error.ProblemType;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
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

@DisplayName("Testes de Integração - Endpoint de refresh token")
class AuthRefreshTokenIntegrationTest extends AbstractIntegrationTest {

  private static final String REFRESH_ENDPOINT = "/api/v1/auth/refresh";
  private static final String PASSWORD = "secure-password";

  private final UserTestDataFactory userTestDataFactory;
  private final RefreshTokenJpaRepository refreshTokenJpaRepository;
  private final SecureTokenGeneratorPort secureTokenGeneratorPort;
  private final JwtDecoder jwtDecoder;

  @Autowired
  AuthRefreshTokenIntegrationTest(
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
  @DisplayName("Deve retornar 200, rotacionar o refresh token e emitir novo access token")
  void shouldRotateRefreshTokenAndIssueNewAccessToken() {
    // Arrange
    UserEntity user =
        userTestDataFactory.createActiveUser(
            "refresh-success.integration@example.com", PASSWORD);
    String currentRawRefreshToken = login(user.getEmail(), PASSWORD, "login-before-refresh").refreshToken();
    String currentTokenHash = secureTokenGeneratorPort.hashToken(currentRawRefreshToken);

    // Act
    Response response = refresh(currentRawRefreshToken, "refresh-active-token");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .header(HttpHeaders.SET_COOKIE, not(blankOrNullString()))
        .body("newAccessToken", not(blankOrNullString()));

    String replacementRawRefreshToken = response.cookie("refreshToken");
    String replacementAccessToken = response.path("newAccessToken");
    String setCookieHeader = response.header(HttpHeaders.SET_COOKIE);
    HttpCookie replacementCookie = HttpCookie.parse(setCookieHeader).getFirst();
    var cookieAttributes =
        Arrays.stream(setCookieHeader.split(";"))
            .skip(1)
            .map(String::trim)
            .toList();

    assertThat(replacementRawRefreshToken).isNotBlank();
    assertThat(response.jsonPath().getMap("$")).doesNotContainKeys("refreshToken", "newRefreshToken");
    assertThat(replacementCookie.getName()).isEqualTo("refreshToken");
    assertThat(replacementCookie.isHttpOnly()).isTrue();
    assertThat(replacementCookie.getSecure()).isTrue();
    assertThat(replacementCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(replacementCookie.getMaxAge()).isEqualTo(Duration.ofDays(7).toSeconds());
    assertThat(cookieAttributes).contains("SameSite=Strict");

    Jwt jwt = jwtDecoder.decode(replacementAccessToken);
    assertThat(jwt.getSubject()).isEqualTo(user.getId().toString());
    assertThat(jwt.getClaimAsString("plan")).isEqualTo(PlanType.FREE.name());
    assertThat(jwt.getClaimAsStringList("authorities"))
        .containsExactlyInAnyOrder(
            "ROLE_USER",
            "url:create",
            "url:read:own",
            "url:ranking:own",
            "url:list:own",
            "url:delete:own");
    assertThat(jwt.getExpiresAt()).isAfter(Instant.now());

    String replacementTokenHash =
        secureTokenGeneratorPort.hashToken(replacementRawRefreshToken);
    RefreshTokenEntity currentToken =
        refreshTokenJpaRepository.findByTokenHash(currentTokenHash).orElseThrow();
    RefreshTokenEntity replacementToken =
        refreshTokenJpaRepository.findByTokenHash(replacementTokenHash).orElseThrow();

    assertThat(refreshTokenJpaRepository.count()).isEqualTo(2);
    assertThat(currentToken.getRevokedAt()).isNotNull();
    assertThat(currentToken.getReplacedByToken().getId()).isEqualTo(replacementToken.getId());
    assertThat(replacementToken.getUser().getId()).isEqualTo(user.getId());
    assertThat(replacementToken.getRevokedAt()).isNull();
    assertThat(replacementToken.getReplacedByToken()).isNull();
    assertThat(replacementToken.getExpiresAt()).isAfter(replacementToken.getCreatedAt());
  }

  @Test
  @DisplayName("Deve retornar 401 quando o cookie de refresh token estiver ausente")
  void shouldRejectRequestWithoutRefreshTokenCookie() {
    // Arrange

    // Act
    Response response = refreshWithoutCookie("refresh-without-cookie");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(401)
        .contentType("application/problem+json")
        .header(HttpHeaders.SET_COOKIE, blankOrNullString())
        .body("title", is("Não autorizado"))
        .body("type", is(ProblemType.UNAUTHORIZED))
        .body("detail", is(IamErrorCode.AUTH_REFRESH_TOKEN_INVALID.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_REFRESH_TOKEN_INVALID.getCode()));

    assertThat(refreshTokenJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName("Deve retornar 401 quando o refresh token não estiver armazenado")
  void shouldRejectUnknownRefreshToken() {
    // Arrange
    String unknownRefreshToken = "unknown-refresh-token";

    // Act
    Response response = refresh(unknownRefreshToken, "refresh-unknown-token");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(401)
        .contentType("application/problem+json")
        .header(HttpHeaders.SET_COOKIE, blankOrNullString())
        .body("title", is("Não autorizado"))
        .body("type", is(ProblemType.UNAUTHORIZED))
        .body("detail", is(IamErrorCode.AUTH_REFRESH_TOKEN_EXPIRED.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_REFRESH_TOKEN_EXPIRED.getCode()));

    assertThat(refreshTokenJpaRepository.count()).isZero();
  }

  @Test
  @DisplayName("Deve retornar 401 e revogar todos os tokens ao detectar replay")
  void shouldRevokeAllUserTokensWhenRotatedRefreshTokenIsReused() {
    // Arrange
    UserEntity user =
        userTestDataFactory.createActiveUser(
            "refresh-replay.integration@example.com", PASSWORD);
    String originalRawRefreshToken =
        login(user.getEmail(), PASSWORD, "login-before-replay").refreshToken();
    Response firstResponse = refresh(originalRawRefreshToken, "refresh-before-replay");

    // Act
    Response replayResponse = refresh(originalRawRefreshToken, "refresh-replayed-token");

    // Assert
    firstResponse.then().log().ifValidationFails().statusCode(200);
    replayResponse
        .then()
        .log()
        .ifValidationFails()
        .statusCode(401)
        .contentType("application/problem+json")
        .header(HttpHeaders.SET_COOKIE, blankOrNullString())
        .body("title", is("Não autorizado"))
        .body("type", is(ProblemType.UNAUTHORIZED))
        .body("detail", is(IamErrorCode.AUTH_REFRESH_TOKEN_COMPROMISED.getMessage()))
        .body("errorCode", is(IamErrorCode.AUTH_REFRESH_TOKEN_COMPROMISED.getCode()));

    assertThat(refreshTokenJpaRepository.findAll())
        .hasSize(2)
        .allSatisfy(token -> assertThat(token.getRevokedAt()).isNotNull());
  }

  private Response refresh(String rawRefreshToken, String idempotencyKey) {
    return given()
        .header("Idempotency-Key", idempotencyKey)
        .cookie("refreshToken", rawRefreshToken)
        .when()
        .post(REFRESH_ENDPOINT);
  }

  private Response refreshWithoutCookie(String idempotencyKey) {
    return given()
        .header("Idempotency-Key", idempotencyKey)
        .when()
        .post(REFRESH_ENDPOINT);
  }
}
