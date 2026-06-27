package com.app.url_shortener.url.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.is;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.ConcurrentTestExecutor;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.security.jwt.JwtAccessTokenSubject;
import com.app.url_shortener.security.jwt.JwtTokenService;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.url.domain.exception.UrlErrorCode;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@DisplayName("Testes de Integração - Exclusão de URL encurtada")
class UrlDeleteIntegrationTest extends AbstractIntegrationTest {

  private static final String URLS_ENDPOINT = "/api/v1/urls";
  private static final String REDIRECT_ENDPOINT = "/r";
  private static final String PASSWORD = "secure-password";
  private static final String REDIRECT_CACHE_KEY_PREFIX = "url:redirect:";

  private final UserTestDataFactory userTestDataFactory;
  private final DynamoDbTable<UrlEntity> urlTable;
  private final StringRedisTemplate stringRedisTemplate;
  private final JwtTokenService jwtTokenService;

  @Autowired
  UrlDeleteIntegrationTest(
      UserTestDataFactory userTestDataFactory,
      DynamoDbTable<UrlEntity> urlTable,
      StringRedisTemplate stringRedisTemplate,
      JwtTokenService jwtTokenService) {
    this.userTestDataFactory = userTestDataFactory;
    this.urlTable = urlTable;
    this.stringRedisTemplate = stringRedisTemplate;
    this.jwtTokenService = jwtTokenService;
  }

  @Test
  @DisplayName("Deve retornar 204 e marcar URL própria como deletada")
  void shouldDeleteOwnActiveUrl() {
    // Arrange
    String email = "delete-own-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-delete-own-url");
    String shortCode = createUrl(session.accessToken(), "https://example.com/delete-own", "create-delete-own-url");
    UrlEntity activeUrl = findByShortCode(shortCode);

    // Act
    Response response = requestDeleteUrl(session.accessToken(), shortCode);

    // Assert
    response.then().log().ifValidationFails().statusCode(204);
    assertThat(response.asString()).isEmpty();

    UrlEntity deletedUrl = findByShortCode(shortCode);
    assertThat(deletedUrl).isNotNull();
    assertThat(deletedUrl.getUserId()).isEqualTo(user.getId());
    assertThat(deletedUrl.getOriginalUrl()).isEqualTo("https://example.com/delete-own");
    assertThat(deletedUrl.getStatus()).isEqualTo(UrlStatus.DELETED);
    assertThat(deletedUrl.getDeletedAt()).isNotNull();
    assertThat(deletedUrl.getDeletedBy()).isEqualTo(user.getId());
    assertThat(deletedUrl.getUpdatedAt()).isNotNull();
    assertThat(deletedUrl.getUpdatedAt()).isAfterOrEqualTo(activeUrl.getUpdatedAt());
    assertThat(deletedUrl.getCreatedAt()).isEqualTo(activeUrl.getCreatedAt());
    assertThat(deletedUrl.getAccessCount()).isZero();
    assertThat(deletedUrl.getLastAccessedAt()).isNull();
    assertThat(deletedUrl.getActiveRankingUserIdGsi()).isNull();
    assertThat(deletedUrl.getStatusCreatedAtShortCodeGsi()).startsWith(UrlStatus.DELETED.name() + "#");
    assertDeletedRedirectCache(shortCode);
  }

  @Test
  @DisplayName("Deve retornar 204 quando administrador deletar URL de outro usuário")
  void shouldDeleteOtherUserUrlWhenRequesterIsAdmin() {
    // Arrange
    String ownerEmail = "delete-admin-owner-url.integration@example.com";
    String adminEmail = "delete-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    UserEntity admin = userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession ownerSession = login(ownerEmail, PASSWORD, "login-delete-admin-owner-url");
    AuthenticatedSession adminSession = login(adminEmail, PASSWORD, "login-delete-admin-url");
    String shortCode =
        createUrl(
            ownerSession.accessToken(),
            "https://example.com/delete-admin",
            "create-delete-admin-url");

    // Act
    Response response = requestDeleteUrl(adminSession.accessToken(), shortCode);

    // Assert
    response.then().log().ifValidationFails().statusCode(204);

    UrlEntity deletedUrl = findByShortCode(shortCode);
    assertThat(deletedUrl).isNotNull();
    assertThat(deletedUrl.getUserId()).isEqualTo(owner.getId());
    assertThat(deletedUrl.getStatus()).isEqualTo(UrlStatus.DELETED);
    assertThat(deletedUrl.getDeletedBy()).isEqualTo(admin.getId());
    assertDeletedRedirectCache(shortCode);
  }

  @Test
  @DisplayName("Deve retornar 403 quando usuário tentar deletar URL de outro usuário")
  void shouldReturnForbiddenWhenRequesterDoesNotOwnUrl() {
    // Arrange
    String ownerEmail = "delete-forbidden-owner-url.integration@example.com";
    String requesterEmail = "delete-forbidden-requester-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createActiveUser(requesterEmail, PASSWORD);
    AuthenticatedSession ownerSession =
        login(ownerEmail, PASSWORD, "login-delete-forbidden-owner-url");
    AuthenticatedSession requesterSession =
        login(requesterEmail, PASSWORD, "login-delete-forbidden-requester-url");
    String shortCode =
        createUrl(
            ownerSession.accessToken(),
            "https://example.com/delete-forbidden",
            "create-delete-forbidden-url");

    // Act
    Response response = requestDeleteUrl(requesterSession.accessToken(), shortCode);

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(403)
        .contentType("application/problem+json")
        .body("title", is("Proibido"))
        .body("type", is(ProblemType.FORBIDDEN))
        .body("detail", is(UrlErrorCode.URL_DELETE_FORBIDDEN.getMessage()))
        .body("errorCode", is(UrlErrorCode.URL_DELETE_FORBIDDEN.getCode()));

    UrlEntity persisted = findByShortCode(shortCode);
    assertThat(persisted).isNotNull();
    assertThat(persisted.getUserId()).isEqualTo(owner.getId());
    assertThat(persisted.getStatus()).isEqualTo(UrlStatus.ACTIVE);
    assertThat(persisted.getDeletedAt()).isNull();
    assertThat(persisted.getDeletedBy()).isNull();
    assertActiveRedirectCache(shortCode, "https://example.com/delete-forbidden");
  }

  @Test
  @DisplayName("Deve retornar 404 quando a URL não existir")
  void shouldReturnNotFoundWhenShortCodeDoesNotExist() {
    // Arrange
    String email = "delete-missing-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-delete-missing-url");

    // Act
    Response response = requestDeleteUrl(session.accessToken(), "Missing123");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(404)
        .contentType("application/problem+json")
        .body("title", is("Não encontrado"))
        .body("type", is(ProblemType.NOT_FOUND))
        .body("detail", is(UrlErrorCode.URL_NOT_FOUND.getMessage()))
        .body("errorCode", is(UrlErrorCode.URL_NOT_FOUND.getCode()));

    assertThat(urlTable.scan().items()).isEmpty();
    assertThat(stringRedisTemplate.hasKey(redirectCacheKey("Missing123"))).isFalse();
  }

  @Test
  @DisplayName("Deve retornar 204 ao deletar novamente uma URL já deletada")
  void shouldReturnNoContentWhenDeletingAlreadyDeletedUrl() {
    // Arrange
    String email = "delete-already-deleted-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-delete-already-deleted-url");
    String shortCode =
        createUrl(
            session.accessToken(),
            "https://example.com/delete-already-deleted",
            "create-delete-already-deleted-url");

    requestDeleteUrl(session.accessToken(), shortCode).then().log().ifValidationFails().statusCode(204);
    UrlEntity firstDeletedState = findByShortCode(shortCode);

    // Act
    Response response = requestDeleteUrl(session.accessToken(), shortCode);

    // Assert
    response.then().log().ifValidationFails().statusCode(204);

    UrlEntity secondDeletedState = findByShortCode(shortCode);
    assertThat(secondDeletedState.getStatus()).isEqualTo(UrlStatus.DELETED);
    assertThat(secondDeletedState.getDeletedAt()).isEqualTo(firstDeletedState.getDeletedAt());
    assertThat(secondDeletedState.getDeletedBy()).isEqualTo(firstDeletedState.getDeletedBy());
    assertDeletedRedirectCache(shortCode);
  }

  @Test
  @DisplayName("Deve manter estado consistente quando deletes concorrentes atingem a mesma URL")
  void shouldKeepConsistentStateWhenSameUrlIsDeletedConcurrently() throws Exception {
    // Arrange
    int workers = 8;
    String email = "delete-concurrent-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-delete-concurrent-url");
    String shortCode =
        createUrl(
            session.accessToken(),
            "https://example.com/delete-concurrent",
            "create-delete-concurrent-url");

    // Act
    List<Response> responses =
        ConcurrentTestExecutor.execute(
            workers, () -> requestDeleteUrl(session.accessToken(), shortCode));

    // Assert
    assertThat(responses).hasSize(workers);
    assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(204));

    UrlEntity deletedUrl = findByShortCode(shortCode);
    assertThat(deletedUrl).isNotNull();
    assertThat(deletedUrl.getStatus()).isEqualTo(UrlStatus.DELETED);
    assertThat(deletedUrl.getDeletedAt()).isNotNull();
    assertThat(deletedUrl.getDeletedBy()).isEqualTo(user.getId());
    assertDeletedRedirectCache(shortCode);
  }

  @Test
  @DisplayName("Deve retornar 401 quando token estiver ausente")
  void shouldReturnUnauthorizedWhenAccessTokenIsMissing() {
    // Arrange

    // Act
    Response response = given().when().delete(URLS_ENDPOINT + "/Missing123");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(401)
        .contentType("application/problem+json")
        .body("title", is("Não autorizado"))
        .body("type", is(ProblemType.UNAUTHORIZED))
        .body("detail", is(CommonErrorCode.AUTH_UNAUTHORIZED.getMessage()))
        .body("errorCode", is(CommonErrorCode.AUTH_UNAUTHORIZED.getCode()));

    assertThat(urlTable.scan().items()).isEmpty();
  }

  @Test
  @DisplayName("Deve retornar 401 quando token for inválido")
  void shouldReturnUnauthorizedWhenAccessTokenIsInvalid() {
    // Arrange

    // Act
    Response response =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .when()
            .delete(URLS_ENDPOINT + "/Missing123");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(401)
        .contentType("application/problem+json")
        .body("title", is("Não autorizado"))
        .body("type", is(ProblemType.UNAUTHORIZED))
        .body("detail", is(CommonErrorCode.AUTH_UNAUTHORIZED.getMessage()))
        .body("errorCode", is(CommonErrorCode.AUTH_UNAUTHORIZED.getCode()));

    assertThat(urlTable.scan().items()).isEmpty();
  }

  @Test
  @DisplayName("Deve retornar 403 quando usuário autenticado não tiver permissão de exclusão")
  void shouldReturnForbiddenWhenAuthenticatedUserDoesNotHaveDeleteAuthority() {
    // Arrange
    String ownerEmail = "delete-without-authority-owner-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    AuthenticatedSession ownerSession =
        login(ownerEmail, PASSWORD, "login-delete-without-authority-owner-url");
    String shortCode =
        createUrl(
            ownerSession.accessToken(),
            "https://example.com/delete-without-authority",
            "create-delete-without-authority-url");
    String accessTokenWithoutDeleteAuthority =
        jwtTokenService.generateAccessToken(
            new JwtAccessTokenSubject(
                owner.getId(), PlanType.FREE.name(), List.of("url:read:own")));

    // Act
    Response response = requestDeleteUrl(accessTokenWithoutDeleteAuthority, shortCode);

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(403)
        .contentType("application/problem+json")
        .body("title", is("Acesso negado"))
        .body("type", is(ProblemType.FORBIDDEN))
        .body("detail", is(CommonErrorCode.AUTH_ACCESS_DENIED.getMessage()))
        .body("errorCode", is(CommonErrorCode.AUTH_ACCESS_DENIED.getCode()));

    UrlEntity persisted = findByShortCode(shortCode);
    assertThat(persisted).isNotNull();
    assertThat(persisted.getStatus()).isEqualTo(UrlStatus.ACTIVE);
    assertThat(persisted.getDeletedAt()).isNull();
    assertThat(persisted.getDeletedBy()).isNull();
    assertActiveRedirectCache(shortCode, "https://example.com/delete-without-authority");
  }

  @Test
  @DisplayName("Deve retornar 404 no redirecionamento após deletar URL")
  void shouldReturnNotFoundWhenRedirectingDeletedUrl() {
    // Arrange
    String email = "delete-redirect-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-delete-redirect-url");
    String shortCode =
        createUrl(
            session.accessToken(),
            "https://example.com/delete-redirect",
            "create-delete-redirect-url");

    requestDeleteUrl(session.accessToken(), shortCode).then().log().ifValidationFails().statusCode(204);

    // Act
    Response response = given().when().get(REDIRECT_ENDPOINT + "/" + shortCode);

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(404)
        .contentType("application/problem+json")
        .header(HttpHeaders.LOCATION, blankOrNullString())
        .body("title", is("Não encontrado"))
        .body("type", is(ProblemType.NOT_FOUND))
        .body("detail", is(UrlErrorCode.URL_NOT_FOUND.getMessage()))
        .body("errorCode", is(UrlErrorCode.URL_NOT_FOUND.getCode()));

    UrlEntity deletedUrl = findByShortCode(shortCode);
    assertThat(deletedUrl.getStatus()).isEqualTo(UrlStatus.DELETED);
    assertThat(deletedUrl.getAccessCount()).isZero();
    assertThat(deletedUrl.getLastAccessedAt()).isNull();
    assertDeletedRedirectCache(shortCode);
  }

  @Test
  @DisplayName("Deve retornar detalhes da URL deletada para o dono")
  void shouldReturnDeletedUrlDetailsForOwner() {
    // Arrange
    String email = "delete-details-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-delete-details-url");
    String shortCode =
        createUrl(
            session.accessToken(),
            "https://example.com/delete-details",
            "create-delete-details-url");

    requestDeleteUrl(session.accessToken(), shortCode).then().log().ifValidationFails().statusCode(204);
    UrlEntity deletedUrl = findByShortCode(shortCode);

    // Act
    Response response =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
            .when()
            .get(URLS_ENDPOINT + "/" + shortCode);

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("shortCode", is(shortCode))
        .body("originalUrl", is("https://example.com/delete-details"))
        .body("userId", is(user.getId().toString()))
        .body("status", is(UrlStatus.DELETED.name()))
        .body("deletedBy", is(user.getId().toString()));

    assertThat((String) response.path("deletedAt")).isEqualTo(deletedUrl.getDeletedAt().toString());
  }

  private String createUrl(String accessToken, String originalUrl, String idempotencyKey) {
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header("Idempotency-Key", idempotencyKey)
            .body(requestBody(originalUrl))
            .when()
            .post(URLS_ENDPOINT)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .extract()
            .response();

    return response.path("shortCode");
  }

  private Response requestDeleteUrl(String accessToken, String shortCode) {
    return given()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .when()
        .delete(URLS_ENDPOINT + "/" + shortCode);
  }

  private static String requestBody(String originalUrl) {
    return """
        {
          "originalUrl": "%s"
        }
        """
        .formatted(originalUrl);
  }

  private UrlEntity findByShortCode(String shortCode) {
    return urlTable.getItem(Key.builder().partitionValue(shortCode).build());
  }

  private void assertActiveRedirectCache(String shortCode, String originalUrl) {
    String cachedValue = stringRedisTemplate.opsForValue().get(redirectCacheKey(shortCode));
    Long ttl = stringRedisTemplate.getExpire(redirectCacheKey(shortCode));

    assertThat(cachedValue).contains("\"status\":\"ACTIVE\"");
    assertThat(cachedValue).contains("\"originalUrl\":\"" + originalUrl + "\"");
    assertThat(ttl).isNotNull().isPositive();
  }

  private void assertDeletedRedirectCache(String shortCode) {
    String cachedValue = stringRedisTemplate.opsForValue().get(redirectCacheKey(shortCode));
    Long ttl = stringRedisTemplate.getExpire(redirectCacheKey(shortCode));

    assertThat(cachedValue).contains("\"status\":\"DELETED\"");
    assertThat(cachedValue).contains("\"originalUrl\":null");
    assertThat(ttl).isNotNull().isPositive();
  }

  private static String redirectCacheKey(String shortCode) {
    return REDIRECT_CACHE_KEY_PREFIX + shortCode;
  }
}
