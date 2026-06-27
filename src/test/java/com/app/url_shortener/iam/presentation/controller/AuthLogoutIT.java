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
import io.restassured.response.Response;
import java.net.HttpCookie;
import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;

@DisplayName("Testes de Integração - Endpoint de logout")
class AuthLogoutIT extends AbstractIntegrationTest {

  private static final String LOGOUT_ENDPOINT = "/api/v1/auth/logout";
  private static final String PASSWORD = "secure-password";

  private final UserTestDataFactory userTestDataFactory;
  private final RefreshTokenJpaRepository refreshTokenJpaRepository;
  private final SecureTokenGeneratorPort secureTokenGeneratorPort;

  @Autowired
  AuthLogoutIT(
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
    UserEntity user =
        userTestDataFactory.createActiveUser("logout-success.integration@example.com", PASSWORD);
    AuthenticatedSession session = login(user.getEmail(), PASSWORD, "login-before-logout");
    String refreshTokenHash = secureTokenGeneratorPort.hashToken(session.refreshToken());

    // Act
    Response response =
        logout(session.accessToken(), session.refreshToken(), "logout-active-session");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(204)
        .header(HttpHeaders.SET_COOKIE, not(blankOrNullString()));

    RefreshTokenEntity savedToken =
        refreshTokenJpaRepository.findByTokenHash(refreshTokenHash).orElseThrow();
    assertThat(savedToken.getRevokedAt()).isNotNull();
    assertThat(response.asString()).isEmpty();
    assertExpiredRefreshTokenCookie(response);
  }

  @Test
  @DisplayName("Deve retornar 401 sem revogar o refresh token quando não houver autenticação")
  void shouldRejectUnauthenticatedLogoutWithoutRevokingRefreshToken() {
    // Arrange
    UserEntity user =
        userTestDataFactory.createActiveUser(
            "logout-unauthenticated.integration@example.com", PASSWORD);
    AuthenticatedSession session =
        login(user.getEmail(), PASSWORD, "login-before-unauthenticated-logout");
    String refreshTokenHash = secureTokenGeneratorPort.hashToken(session.refreshToken());

    // Act
    Response response =
        logoutWithoutAuthorization(session.refreshToken(), "logout-without-authorization");

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
        .body("detail", is(CommonErrorCode.AUTH_UNAUTHORIZED.getMessage()))
        .body("errorCode", is(CommonErrorCode.AUTH_UNAUTHORIZED.getCode()));

    RefreshTokenEntity savedToken =
        refreshTokenJpaRepository.findByTokenHash(refreshTokenHash).orElseThrow();
    assertThat(savedToken.getRevokedAt()).isNull();
  }

  @Test
  @DisplayName("Deve retornar 204 e expirar o cookie quando o refresh token estiver ausente")
  void shouldExpireCookieWithoutRevokingTokenWhenRefreshTokenCookieIsMissing() {
    // Arrange
    UserEntity user =
        userTestDataFactory.createActiveUser(
            "logout-without-cookie.integration@example.com", PASSWORD);
    AuthenticatedSession session =
        login(user.getEmail(), PASSWORD, "login-before-logout-without-cookie");
    String refreshTokenHash = secureTokenGeneratorPort.hashToken(session.refreshToken());

    // Act
    Response response = logout(session.accessToken(), null, "logout-without-refresh-cookie");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(204)
        .header(HttpHeaders.SET_COOKIE, not(blankOrNullString()));

    RefreshTokenEntity savedToken =
        refreshTokenJpaRepository.findByTokenHash(refreshTokenHash).orElseThrow();

    assertThat(savedToken.getRevokedAt()).isNull();
    assertExpiredRefreshTokenCookie(response);
  }

  @Test
  @DisplayName("Deve retornar 204 e expirar o cookie quando o refresh token for desconhecido")
  void shouldExpireCookieWithoutRevokingTokenWhenRefreshTokenCookieIsUnknown() {
    // Arrange
    UserEntity user =
        userTestDataFactory.createActiveUser(
            "logout-unknown-token.integration@example.com", PASSWORD);
    AuthenticatedSession session =
        login(user.getEmail(), PASSWORD, "login-before-logout-unknown-token");
    String knownRefreshTokenHash = secureTokenGeneratorPort.hashToken(session.refreshToken());
    String unknownRefreshToken = "unknown-refresh-token";
    String unknownRefreshTokenHash = secureTokenGeneratorPort.hashToken(unknownRefreshToken);

    // Act
    Response response =
        logout(session.accessToken(), unknownRefreshToken, "logout-unknown-refresh-cookie");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(204)
        .header(HttpHeaders.SET_COOKIE, not(blankOrNullString()));

    RefreshTokenEntity savedToken =
        refreshTokenJpaRepository.findByTokenHash(knownRefreshTokenHash).orElseThrow();

    assertThat(refreshTokenJpaRepository.findByTokenHash(unknownRefreshTokenHash)).isEmpty();
    assertThat(refreshTokenJpaRepository.count()).isEqualTo(1);
    assertThat(savedToken.getRevokedAt()).isNull();
    assertExpiredRefreshTokenCookie(response);
  }

  private Response logout(String accessToken, String refreshToken, String idempotencyKey) {
    var request =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("Idempotency-Key", idempotencyKey);

    if (refreshToken != null) {
      request.cookie("refreshToken", refreshToken);
    }

    return given().spec(request).when().post(LOGOUT_ENDPOINT);
  }

  private Response logoutWithoutAuthorization(String refreshToken, String idempotencyKey) {
    return given()
        .header("Idempotency-Key", idempotencyKey)
        .cookie("refreshToken", refreshToken)
        .when()
        .post(LOGOUT_ENDPOINT);
  }

  private void assertExpiredRefreshTokenCookie(Response response) {
    String setCookieHeader = response.header(HttpHeaders.SET_COOKIE);
    HttpCookie expiredCookie = HttpCookie.parse(setCookieHeader).getFirst();
    var cookieAttributes =
        Arrays.stream(setCookieHeader.split(";")).skip(1).map(String::trim).toList();

    assertThat(expiredCookie.getName()).isEqualTo("refreshToken");
    assertThat(expiredCookie.getValue()).isEmpty();
    assertThat(expiredCookie.getPath()).isEqualTo("/api/v1/auth");
    assertThat(expiredCookie.getMaxAge()).isZero();
    assertThat(expiredCookie.getSecure()).isTrue();
    assertThat(expiredCookie.isHttpOnly()).isTrue();
    assertThat(cookieAttributes).contains("SameSite=Strict");
  }
}
