package com.app.url_shortener.iam.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import com.app.url_shortener.iam.infrastructure.entity.RefreshTokenEntity;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.iam.infrastructure.repository.RefreshTokenJpaRepository;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import java.net.HttpCookie;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

class AuthLogoutIntegrationTest extends AbstractIntegrationTest {

  private static final String LOGOUT_ENDPOINT = "/api/v1/auth/logout";
  private static final String PASSWORD = "secure-password";

  private final UserTestDataFactory userTestDataFactory;
  private final RefreshTokenJpaRepository refreshTokenJpaRepository;
  private final SecureTokenGeneratorPort secureTokenGeneratorPort;

  @Autowired
  AuthLogoutIntegrationTest(
      UserTestDataFactory userTestDataFactory,
      RefreshTokenJpaRepository refreshTokenJpaRepository,
      SecureTokenGeneratorPort secureTokenGeneratorPort) {
    this.userTestDataFactory = userTestDataFactory;
    this.refreshTokenJpaRepository = refreshTokenJpaRepository;
    this.secureTokenGeneratorPort = secureTokenGeneratorPort;
  }

  @Test
  @DisplayName("Deve retornar 204, revogar o refresh token e expirar o cookie")
  void shouldRevokeRefreshTokenAndExpireCookieForAuthenticatedUser() {
    // Arrange
    UserEntity user = userTestDataFactory.createActiveUser("logout-success.integration@example.com", PASSWORD);
    AuthenticatedSession session = login(user.getEmail(), PASSWORD, "login-before-logout");
    String refreshTokenHash = secureTokenGeneratorPort.hashToken(session.refreshToken());

    // Act
    var response =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
            .header("Idempotency-Key", "logout-active-session")
            .cookie("refreshToken", session.refreshToken())
            .when()
            .post(LOGOUT_ENDPOINT)
            .then()
            .statusCode(204)
            .header(HttpHeaders.SET_COOKIE, not(blankOrNullString()))
            .extract()
            .response();

    // Assert
    RefreshTokenEntity savedToken = refreshTokenJpaRepository.findByTokenHash(refreshTokenHash).orElseThrow();
    String setCookieHeader = response.header(HttpHeaders.SET_COOKIE);
    HttpCookie expiredCookie = HttpCookie.parse(setCookieHeader).getFirst();
    var cookieAttributes =
        Arrays.stream(setCookieHeader.split(";"))
            .skip(1)
            .map(String::trim)
            .toList();

    assertThat(savedToken.getRevokedAt()).isNotNull();
    assertThat(response.asString()).isEmpty();
    assertThat(expiredCookie.getName()).isEqualTo("refreshToken");
    assertThat(expiredCookie.getValue()).isEmpty();
    assertThat(expiredCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(expiredCookie.getMaxAge()).isZero();
    assertThat(expiredCookie.getSecure()).isTrue();
    assertThat(expiredCookie.isHttpOnly()).isTrue();
    assertThat(cookieAttributes).contains("SameSite=Strict");
  }

  @Test
  @DisplayName("Deve retornar 401 sem revogar o refresh token quando não houver autenticação")
  void shouldRejectUnauthenticatedLogoutWithoutRevokingRefreshToken() {
    // Arrange
    UserEntity user = userTestDataFactory.createActiveUser("logout-unauthenticated.integration@example.com", PASSWORD);
    AuthenticatedSession session = login(user.getEmail(), PASSWORD, "login-before-unauthenticated-logout");
    String refreshTokenHash = secureTokenGeneratorPort.hashToken(session.refreshToken());

    // Act
    given()
        .header("Idempotency-Key", "logout-without-authorization")
        .cookie("refreshToken", session.refreshToken())
        .when()
        .post(LOGOUT_ENDPOINT)
        .then()
        .statusCode(401)
        .contentType("application/problem+json")
        .header(HttpHeaders.SET_COOKIE, blankOrNullString())
        .body("title", is("Não autorizado"))
        .body("type", is(ProblemType.UNAUTHORIZED))
        .body("detail", is(CommonErrorCode.AUTH_UNAUTHORIZED.getMessage()))
        .body("errorCode", is(CommonErrorCode.AUTH_UNAUTHORIZED.getCode()));

    // Assert
    RefreshTokenEntity savedToken = refreshTokenJpaRepository.findByTokenHash(refreshTokenHash).orElseThrow();
    assertThat(savedToken.getRevokedAt()).isNull();
  }

  @Test
  @DisplayName("Deve retornar 204 e expirar o cookie quando o refresh token estiver ausente")
  void shouldExpireCookieWithoutRevokingTokenWhenRefreshTokenCookieIsMissing() {
    // Arrange
    UserEntity user = userTestDataFactory.createActiveUser("logout-without-cookie.integration@example.com", PASSWORD);
    AuthenticatedSession session = login(user.getEmail(), PASSWORD, "login-before-logout-without-cookie");
    String refreshTokenHash = secureTokenGeneratorPort.hashToken(session.refreshToken());

    // Act
    var response =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
            .header("Idempotency-Key", "logout-without-refresh-cookie")
            .when()
            .post(LOGOUT_ENDPOINT)
            .then()
            .statusCode(204)
            .header(HttpHeaders.SET_COOKIE, not(blankOrNullString()))
            .extract()
            .response();

    // Assert
    RefreshTokenEntity savedToken = refreshTokenJpaRepository.findByTokenHash(refreshTokenHash).orElseThrow();
    HttpCookie expiredCookie = HttpCookie.parse(response.header(HttpHeaders.SET_COOKIE)).getFirst();

    assertThat(savedToken.getRevokedAt()).isNull();
    assertThat(expiredCookie.getName()).isEqualTo("refreshToken");
    assertThat(expiredCookie.getValue()).isEmpty();
    assertThat(expiredCookie.getMaxAge()).isZero();
  }
}
