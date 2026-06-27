package com.app.url_shortener.url.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.ConcurrentTestExecutor;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.shared.config.ApplicationProperties;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.url.domain.exception.UrlErrorCode;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@DisplayName("Testes de Integração - Criação de URL encurtada")
class UrlCreateIT extends AbstractIntegrationTest {

  private static final String CREATE_URL_ENDPOINT = "/api/v1/urls";
  private static final String PASSWORD = "secure-password";
  private static final String REDIRECT_CACHE_KEY_PREFIX = "url:redirect:";

  private final UserTestDataFactory userTestDataFactory;
  private final DynamoDbTable<UrlEntity> urlTable;
  private final StringRedisTemplate stringRedisTemplate;
  private final ApplicationProperties applicationProperties;

  @Autowired
  UrlCreateIT(
      UserTestDataFactory userTestDataFactory,
      DynamoDbTable<UrlEntity> urlTable,
      StringRedisTemplate stringRedisTemplate,
      ApplicationProperties applicationProperties) {
    this.userTestDataFactory = userTestDataFactory;
    this.urlTable = urlTable;
    this.stringRedisTemplate = stringRedisTemplate;
    this.applicationProperties = applicationProperties;
  }

  @Test
  @DisplayName("Deve retornar 201 e persistir URL encurtada quando a requisição for válida")
  void shouldCreateShortUrlWhenRequestIsValid() {
    // Arrange
    String email = "create-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-create-url");
    var requestBody = requestBody("https://example.com/resource");

    // Act
    Response response =
        requestCreateUrl(session.accessToken(), requestBody, "create-url-success")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .header(HttpHeaders.LOCATION, not(blankOrNullString()))
            .body("originalUrl", is("https://example.com/resource"))
            .body("shortCode", matchesPattern("[a-zA-Z0-9]+"))
            .body("status", is(UrlStatus.ACTIVE.name()))
            .extract()
            .response();

    // Assert
    String shortCode = response.path("shortCode");
    String shortUrl = response.path("shortUrl");

    assertThat(response.header(HttpHeaders.LOCATION)).isEqualTo(shortUrl);
    assertThat(shortUrl).isEqualTo(applicationProperties.baseUrl() + "/r/" + shortCode);

    UrlEntity persisted = findByShortCode(shortCode);
    assertThat(persisted).isNotNull();
    assertThat(persisted.getUserId()).isEqualTo(user.getId());
    assertThat(persisted.getOriginalUrl()).isEqualTo("https://example.com/resource");
    assertThat(persisted.getStatus()).isEqualTo(UrlStatus.ACTIVE);
    assertThat(persisted.getAccessCount()).isZero();
    assertThat(persisted.getCreatedAt()).isNotNull();
    assertThat(persisted.getCreatedAtShortCodeGsi()).isNotBlank();
    assertThat(persisted.getStatusCreatedAtShortCodeGsi())
        .startsWith(UrlStatus.ACTIVE.name() + "#");
    assertThat(persisted.getActiveRankingUserIdGsi()).isEqualTo(user.getId().toString());
    assertRedirectCache(shortCode, "https://example.com/resource");
  }

  @Test
  @DisplayName("Deve reutilizar resposta idempotente sem criar outra URL")
  void shouldReplayCompletedResponseWhenSameIdempotencyKeyAndPayloadAreUsed() {
    // Arrange
    String email = "idempotent-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-idempotent-url");
    var requestBody = requestBody("https://example.com/idempotent");
    String idempotencyKey = "create-url-idempotent-replay";

    // Act
    Response firstResponse =
        requestCreateUrl(session.accessToken(), requestBody, idempotencyKey)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .extract()
            .response();
    Response replayedResponse =
        requestCreateUrl(session.accessToken(), requestBody, idempotencyKey)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .extract()
            .response();

    // Assert
    String firstShortCode = firstResponse.path("shortCode");
    String replayedShortCode = replayedResponse.path("shortCode");

    assertThat(replayedShortCode).isEqualTo(firstShortCode);
    assertThat(replayedResponse.asString()).isEqualTo(firstResponse.asString());
    assertThat(findByUserId(user.getId().toString())).hasSize(1);
  }

  @Test
  @DisplayName(
      "Deve retornar 409 quando a mesma chave de idempotência for usada com payload diferente")
  void shouldRejectSameIdempotencyKeyWhenPayloadIsDifferent() {
    // Arrange
    String email = "idempotency-conflict-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-idempotency-conflict-url");
    var firstRequestBody = requestBody("https://example.com/first");
    var conflictingRequestBody = requestBody("https://example.com/second");
    String idempotencyKey = "create-url-idempotency-conflict";

    // Act
    requestCreateUrl(session.accessToken(), firstRequestBody, idempotencyKey)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(201)
        .contentType(ContentType.JSON);

    Response conflictingResponse =
        requestCreateUrl(session.accessToken(), conflictingRequestBody, idempotencyKey)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(409)
            .contentType("application/problem+json")
            .body("title", is("Conflito"))
            .body("type", is(ProblemType.CONFLICT))
            .body("detail", is(CommonErrorCode.IDEMPOTENCY_IN_PROCESSING.getMessage()))
            .body("errorCode", is(CommonErrorCode.IDEMPOTENCY_IN_PROCESSING.getCode()))
            .extract()
            .response();

    // Assert
    assertThat(conflictingResponse.statusCode()).isEqualTo(409);

    assertThat(findByUserId(user.getId().toString()))
        .singleElement()
        .satisfies(url -> assertThat(url.getOriginalUrl()).isEqualTo("https://example.com/first"));
  }

  @Test
  @DisplayName("Deve retornar 429 quando usuário FREE exceder o limite de criação")
  void shouldRateLimitFreeUserShortUrlCreation() {
    // Arrange
    String email = "rate-limit-create-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-rate-limit-create-url");

    for (int attempt = 1; attempt <= 5; attempt++) {
      requestCreateUrl(
              session.accessToken(),
              requestBody("https://example.com/rate-limit-" + attempt),
              "create-url-rate-limit-" + attempt)
          .then()
          .log()
          .ifValidationFails()
          .statusCode(201)
          .contentType(ContentType.JSON);
    }

    // Act
    Response response =
        requestCreateUrl(
                session.accessToken(),
                requestBody("https://example.com/rate-limit-blocked"),
                "create-url-rate-limit-blocked")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(429)
            .contentType("application/problem+json")
            .header(HttpHeaders.RETRY_AFTER, matchesPattern("\\d+"))
            .body("title", is("Muitas requisições"))
            .body("type", is(ProblemType.TOO_MANY_REQUESTS))
            .body("detail", is(CommonErrorCode.TOO_MANY_REQUESTS.getMessage()))
            .body("errorCode", is(CommonErrorCode.TOO_MANY_REQUESTS.getCode()))
            .extract()
            .response();

    // Assert
    assertThat(response.statusCode()).isEqualTo(429);
    assertThat(findByUserId(user.getId().toString())).hasSize(5);
  }

  @Test
  @DisplayName("Deve retornar 400 e não persistir URL quando destino for inseguro")
  void shouldRejectUnsafeDestinationWithoutPersistingUrl() {
    // Arrange
    String email = "unsafe-create-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-unsafe-create-url");
    var requestBody = requestBody("http://localhost/admin");

    // Act
    Response response =
        requestCreateUrl(session.accessToken(), requestBody, "create-url-unsafe")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("title", is("Validação"))
            .body("type", is(ProblemType.VALIDATION))
            .body("detail", is(UrlErrorCode.URL_UNSAFE_DESTINATION.getMessage()))
            .body("errorCode", is(UrlErrorCode.URL_UNSAFE_DESTINATION.getCode()))
            .extract()
            .response();

    // Assert
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(findByUserId(user.getId().toString())).isEmpty();
  }

  @Test
  @DisplayName("Deve retornar 400 quando usuário autenticado não envia Idempotency-Key")
  void shouldRequireIdempotencyKeyForAuthenticatedUserBeforeCreatingUrl() {
    // Arrange
    String email = "missing-idempotency-create-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-missing-idempotency-create-url");
    var requestBody = requestBody("https://example.com/missing-idempotency");

    // Act
    Response response =
        given()
            .contentType(ContentType.JSON)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
            .body(requestBody)
            .when()
            .post(CREATE_URL_ENDPOINT)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(400)
            .contentType("application/problem+json")
            .body("title", is("Validação"))
            .body("type", is(ProblemType.VALIDATION))
            .body("detail", is(CommonErrorCode.IDEMPOTENCY_HEADER_MISSING.getMessage()))
            .body("errorCode", is(CommonErrorCode.IDEMPOTENCY_HEADER_MISSING.getCode()))
            .extract()
            .response();

    // Assert
    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(findByUserId(user.getId().toString())).isEmpty();
  }

  @Test
  @DisplayName("Deve retornar 401 antes da validação de idempotência quando token estiver ausente")
  void shouldReturnUnauthorizedBeforeIdempotencyWhenAccessTokenIsMissing() {
    // Arrange
    var requestBody = requestBody("https://example.com/unauthenticated");

    // Act
    Response response =
        given().contentType(ContentType.JSON).body(requestBody).when().post(CREATE_URL_ENDPOINT);

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
  @DisplayName(
      "Deve persistir apenas uma URL quando requisições concorrentes usam a mesma chave de idempotência")
  void shouldPersistOnlyOneUrlWhenSameIdempotencyKeyAndPayloadAreConcurrent() throws Exception {
    // Arrange
    int workers = 8;
    String email = "same-idempotency-concurrent-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-same-idempotency-concurrent-url");
    String requestBody = requestBody("https://example.com/concurrent-idempotent");
    String idempotencyKey = "create-url-same-idempotency-concurrent";

    // Act
    List<Response> responses =
        ConcurrentTestExecutor.execute(
            workers, () -> requestCreateUrl(session.accessToken(), requestBody, idempotencyKey));

    // Assert
    List<Integer> statusCodes = responses.stream().map(Response::statusCode).toList();
    List<UrlEntity> persistedUrls = findByUserId(user.getId().toString());

    assertThat(statusCodes).allMatch(status -> status == 201 || status == 409);
    assertThat(statusCodes).contains(201);
    assertThat(persistedUrls).hasSize(1);

    String persistedShortCode = persistedUrls.getFirst().getShortCode();
    List<Response> createdResponses =
        responses.stream().filter(response -> response.statusCode() == 201).toList();
    assertThat(createdResponses).isNotEmpty();
    assertThat(createdResponses)
        .allSatisfy(
            response ->
                assertThat((String) response.path("shortCode")).isEqualTo(persistedShortCode));
  }

  @Test
  @DisplayName("Deve criar códigos curtos únicos com requisições concorrentes independentes")
  void shouldCreateUniqueShortCodesWhenDifferentIdempotencyKeysAreConcurrent() throws Exception {
    // Arrange
    int workers = 10;
    List<AuthenticatedSession> sessions = new ArrayList<>();

    // Cria 10 usuários PREMIUM e autentica-os
    for (int i = 0; i < workers; i++) {
      String email = "different-idempotency-concurrent-url-" + i + "@integration.example.com";
      userTestDataFactory.createActiveUser(email, PASSWORD, PlanType.PREMIUM);
      var session = login(email, PASSWORD, "login-different-idempotency-concurrent-url-" + i);
      sessions.add(session);
    }

    // Envia 10 requets concorrentes para criar urls
    // Act
    List<Response> responses =
        ConcurrentTestExecutor.execute(
            workers,
            worker ->
                requestCreateUrl(
                    sessions.get(worker - 1).accessToken(),
                    requestBody("https://example.com/concurrent-" + worker),
                    "create-url-different-idempotency-concurrent-" + worker));

    // Assert
    List<String> shortCodes =
        responses.stream()
            .peek(response -> response.then().log().ifValidationFails().statusCode(201))
            .map(response -> (String) response.path("shortCode"))
            .toList();
    assertThat(shortCodes).hasSize(workers).doesNotHaveDuplicates();
    assertThat(shortCodes)
        .allSatisfy(
            shortCode -> {
              String key = REDIRECT_CACHE_KEY_PREFIX + shortCode;
              assertThat(stringRedisTemplate.hasKey(key)).isTrue();
              assertThat(stringRedisTemplate.getExpire(key)).isPositive();
            });

    List<UrlEntity> persistedUrls = urlTable.scan().items().stream().toList();
    assertThat(persistedUrls).hasSize(workers);
    assertThat(persistedUrls)
        .extracting(UrlEntity::getShortCode)
        .containsExactlyInAnyOrderElementsOf(shortCodes);
  }

  private Response requestCreateUrl(String accessToken, String requestBody, String idempotencyKey) {
    return given()
        .contentType(ContentType.JSON)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header("Idempotency-Key", idempotencyKey)
        .body(requestBody)
        .when()
        .post(CREATE_URL_ENDPOINT);
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

  private List<UrlEntity> findByUserId(String userId) {
    return urlTable.scan().items().stream()
        .filter(url -> url.getUserId().toString().equals(userId))
        .toList();
  }

  private void assertRedirectCache(String shortCode, String originalUrl) {
    String key = REDIRECT_CACHE_KEY_PREFIX + shortCode;
    String cachedValue = stringRedisTemplate.opsForValue().get(key);
    Long ttl = stringRedisTemplate.getExpire(key);

    assertThat(cachedValue).contains("\"status\":\"ACTIVE\"");
    assertThat(cachedValue).contains("\"originalUrl\":\"" + originalUrl + "\"");
    assertThat(ttl).isNotNull().isPositive();
  }
}
