package com.app.url_shortener.url.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.is;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.ConcurrentTestExecutor;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.url.domain.exception.UrlErrorCode;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@DisplayName("Testes de Integração - Redirecionamento de URL encurtada")
class RedirectIT extends AbstractIntegrationTest {

  private static final String URLS_ENDPOINT = "/api/v1/urls";
  private static final String REDIRECT_ENDPOINT = "/r";
  private static final String PASSWORD = "secure-password";
  private static final String REDIRECT_CACHE_KEY_PREFIX = "url:redirect:";

  private final UserTestDataFactory userTestDataFactory;
  private final DynamoDbTable<UrlEntity> urlTable;
  private final StringRedisTemplate stringRedisTemplate;

  @Autowired
  RedirectIT(
      UserTestDataFactory userTestDataFactory,
      DynamoDbTable<UrlEntity> urlTable,
      StringRedisTemplate stringRedisTemplate) {
    this.userTestDataFactory = userTestDataFactory;
    this.urlTable = urlTable;
    this.stringRedisTemplate = stringRedisTemplate;
  }

  @Test
  @DisplayName("Deve retornar 302 ao redirecionar URL ativa criada pela API")
  void shouldRedirectActiveUrlCreatedByApi() {
    // Arrange
    String email = "redirect-active-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-redirect-active-url");
    CreatedUrl createdUrl =
        createUrl(
            session.accessToken(),
            "https://example.com/redirect-active",
            "create-redirect-active-url");

    // Act
    Response response = requestRedirect(createdUrl.shortCode());

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(302)
        .header(HttpHeaders.LOCATION, createdUrl.originalUrl());
    assertThat(response.asString()).isEmpty();

    UrlEntity persisted = findByShortCode(createdUrl.shortCode());
    assertThat(persisted).isNotNull();
    assertThat(persisted.getStatus()).isEqualTo(UrlStatus.ACTIVE);
    assertActiveRedirectCache(createdUrl.shortCode(), createdUrl.originalUrl());
  }

  @Test
  @DisplayName("Deve retornar 302 e recriar cache ativo quando Redis estiver sem entrada")
  void shouldRedirectAndRecreateActiveCacheWhenRedisEntryIsMissing() {
    // Arrange
    String email = "redirect-cache-miss-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-redirect-cache-miss-url");
    CreatedUrl createdUrl =
        createUrl(
            session.accessToken(),
            "https://example.com/redirect-cache-miss",
            "create-redirect-cache-miss-url");
    stringRedisTemplate.delete(redirectCacheKey(createdUrl.shortCode()));

    // Act
    Response response = requestRedirect(createdUrl.shortCode());

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(302)
        .header(HttpHeaders.LOCATION, createdUrl.originalUrl());

    UrlEntity persisted = findByShortCode(createdUrl.shortCode());
    assertThat(persisted).isNotNull();
    assertThat(persisted.getStatus()).isEqualTo(UrlStatus.ACTIVE);
    assertActiveRedirectCache(createdUrl.shortCode(), createdUrl.originalUrl());
  }

  @Test
  @DisplayName("Deve retornar 302 usando cache ativo já aquecido")
  void shouldRedirectUsingWarmActiveCache() {
    // Arrange
    String email = "redirect-cache-hit-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-redirect-cache-hit-url");
    CreatedUrl createdUrl =
        createUrl(
            session.accessToken(),
            "https://example.com/redirect-cache-hit",
            "create-redirect-cache-hit-url");
    assertActiveRedirectCache(createdUrl.shortCode(), createdUrl.originalUrl());

    // Act
    Response response = requestRedirect(createdUrl.shortCode());

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(302)
        .header(HttpHeaders.LOCATION, createdUrl.originalUrl());
    assertActiveRedirectCache(createdUrl.shortCode(), createdUrl.originalUrl());
  }

  @Test
  @DisplayName("Deve retornar 404 e salvar cache negativo quando shortcode não existir")
  void shouldReturnNotFoundAndSaveNegativeCacheWhenShortCodeDoesNotExist() {
    // Arrange
    String shortCode = "Missing123";

    // Act
    Response response = requestRedirect(shortCode);

    // Assert
    assertNotFoundRedirectResponse(response);
    assertThat(urlTable.scan().items()).isEmpty();
    assertNotFoundRedirectCache(shortCode);
  }

  @Test
  @DisplayName("Deve retornar 404 quando cache negativo já existir")
  void shouldReturnNotFoundWhenNegativeCacheAlreadyExists() {
    // Arrange
    String shortCode = "Missing123";
    requestRedirect(shortCode).then().log().ifValidationFails().statusCode(404);
    assertNotFoundRedirectCache(shortCode);

    // Act
    Response response = requestRedirect(shortCode);

    // Assert
    assertNotFoundRedirectResponse(response);
    assertNotFoundRedirectCache(shortCode);
  }

  @Test
  @DisplayName("Deve retornar 404 ao redirecionar URL deletada pelo fluxo real")
  void shouldReturnNotFoundWhenRedirectingDeletedUrl() {
    // Arrange
    String email = "redirect-deleted-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-redirect-deleted-url");
    CreatedUrl createdUrl =
        createUrl(
            session.accessToken(),
            "https://example.com/redirect-deleted",
            "create-redirect-deleted-url");
    requestDeleteUrl(session.accessToken(), createdUrl.shortCode())
        .then()
        .log()
        .ifValidationFails()
        .statusCode(204);

    // Act
    Response response = requestRedirect(createdUrl.shortCode());

    // Assert
    assertNotFoundRedirectResponse(response);

    UrlEntity deletedUrl = findByShortCode(createdUrl.shortCode());
    assertThat(deletedUrl).isNotNull();
    assertThat(deletedUrl.getStatus()).isEqualTo(UrlStatus.DELETED);
    assertDeletedRedirectCache(createdUrl.shortCode());
  }

  @Test
  @DisplayName("Deve retornar 404 e recriar cache deletado quando Redis estiver sem entrada")
  void shouldReturnNotFoundAndRecreateDeletedCacheWhenRedisEntryIsMissing() {
    // Arrange
    String email = "redirect-deleted-cache-miss-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-redirect-deleted-cache-miss-url");
    CreatedUrl createdUrl =
        createUrl(
            session.accessToken(),
            "https://example.com/redirect-deleted-cache-miss",
            "create-redirect-deleted-cache-miss-url");
    requestDeleteUrl(session.accessToken(), createdUrl.shortCode())
        .then()
        .log()
        .ifValidationFails()
        .statusCode(204);
    stringRedisTemplate.delete(redirectCacheKey(createdUrl.shortCode()));

    // Act
    Response response = requestRedirect(createdUrl.shortCode());

    // Assert
    assertNotFoundRedirectResponse(response);

    UrlEntity deletedUrl = findByShortCode(createdUrl.shortCode());
    assertThat(deletedUrl).isNotNull();
    assertThat(deletedUrl.getStatus()).isEqualTo(UrlStatus.DELETED);
    assertDeletedRedirectCache(createdUrl.shortCode());
  }

  @Test
  @DisplayName("Deve manter redirecionamento consistente em requisições concorrentes")
  void shouldKeepRedirectConsistentWhenRequestsAreConcurrent() throws Exception {
    // Arrange
    int workers = 8;
    String email = "redirect-concurrent-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-redirect-concurrent-url");
    CreatedUrl createdUrl =
        createUrl(
            session.accessToken(),
            "https://example.com/redirect-concurrent",
            "create-redirect-concurrent-url");

    // Act
    List<Response> responses =
        ConcurrentTestExecutor.execute(workers, () -> requestRedirect(createdUrl.shortCode()));

    // Assert
    assertThat(responses).hasSize(workers);
    assertThat(responses)
        .allSatisfy(
            response -> {
              assertThat(response.statusCode()).isEqualTo(302);
              assertThat(response.header(HttpHeaders.LOCATION)).isEqualTo(createdUrl.originalUrl());
              assertThat(response.asString()).isEmpty();
            });

    UrlEntity persisted = findByShortCode(createdUrl.shortCode());
    assertThat(persisted).isNotNull();
    assertThat(persisted.getStatus()).isEqualTo(UrlStatus.ACTIVE);
    assertActiveRedirectCache(createdUrl.shortCode(), createdUrl.originalUrl());
  }

  private CreatedUrl createUrl(String accessToken, String originalUrl, String idempotencyKey) {
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

    return new CreatedUrl(response.path("shortCode"), originalUrl);
  }

  private Response requestDeleteUrl(String accessToken, String shortCode) {
    return given()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .when()
        .delete(URLS_ENDPOINT + "/" + shortCode);
  }

  private Response requestRedirect(String shortCode) {
    return given().when().get(REDIRECT_ENDPOINT + "/" + shortCode);
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

  private void assertNotFoundRedirectResponse(Response response) {
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

  private void assertNotFoundRedirectCache(String shortCode) {
    String cachedValue = stringRedisTemplate.opsForValue().get(redirectCacheKey(shortCode));
    Long ttl = stringRedisTemplate.getExpire(redirectCacheKey(shortCode));

    assertThat(cachedValue).contains("\"status\":\"NOT_FOUND\"");
    assertThat(cachedValue).contains("\"originalUrl\":null");
    assertThat(ttl).isNotNull().isPositive();
  }

  private static String redirectCacheKey(String shortCode) {
    return REDIRECT_CACHE_KEY_PREFIX + shortCode;
  }

  private record CreatedUrl(String shortCode, String originalUrl) {}
}
