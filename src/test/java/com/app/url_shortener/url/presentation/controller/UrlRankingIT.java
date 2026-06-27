package com.app.url_shortener.url.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.hamcrest.Matchers.is;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.security.jwt.JwtAccessTokenSubject;
import com.app.url_shortener.security.jwt.JwtTokenService;
import com.app.url_shortener.shared.config.ApplicationProperties;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@DisplayName("Testes de Integração - Ranking das próprias URLs")
class UrlRankingIT extends AbstractIntegrationTest {

  private static final String URLS_ENDPOINT = "/api/v1/urls";
  private static final String RANKING_ENDPOINT = URLS_ENDPOINT + "/me/ranking";
  private static final String PASSWORD = "secure-password";

  private final UserTestDataFactory userTestDataFactory;
  private final UrlRepositoryPort urlRepositoryPort;
  private final DynamoDbTable<UrlEntity> urlTable;
  private final ApplicationProperties applicationProperties;
  private final JwtTokenService jwtTokenService;

  @Autowired
  UrlRankingIT(
      UserTestDataFactory userTestDataFactory,
      UrlRepositoryPort urlRepositoryPort,
      DynamoDbTable<UrlEntity> urlTable,
      ApplicationProperties applicationProperties,
      JwtTokenService jwtTokenService) {
    this.userTestDataFactory = userTestDataFactory;
    this.urlRepositoryPort = urlRepositoryPort;
    this.urlTable = urlTable;
    this.applicationProperties = applicationProperties;
    this.jwtTokenService = jwtTokenService;
  }

  @Test
  @DisplayName("Deve retornar 200 com ranking padrão limitado a três URLs")
  void shouldReturnDefaultRankingLimitedToThreeUrls() {
    // Arrange
    String email = "ranking-default-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-ranking-default-url");

    CreatedUrl first =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-default-1",
            "create-ranking-default-1");
    CreatedUrl second =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-default-2",
            "create-ranking-default-2");
    CreatedUrl third =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-default-3",
            "create-ranking-default-3");
    CreatedUrl fourth =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-default-4",
            "create-ranking-default-4");
    Instant firstAccessedAt = Instant.parse("2026-06-01T10:00:00Z");
    Instant secondAccessedAt = Instant.parse("2026-06-01T10:05:00Z");
    Instant thirdAccessedAt = Instant.parse("2026-06-01T10:10:00Z");
    Instant fourthAccessedAt = Instant.parse("2026-06-01T10:15:00Z");
    urlRepositoryPort.incrementAccessCount(first.shortCode(), 5, firstAccessedAt);
    urlRepositoryPort.incrementAccessCount(second.shortCode(), 9, secondAccessedAt);
    urlRepositoryPort.incrementAccessCount(third.shortCode(), 2, thirdAccessedAt);
    urlRepositoryPort.incrementAccessCount(fourth.shortCode(), 12, fourthAccessedAt);
    awaitRankingShortCodes(
        session.accessToken(),
        null,
        List.of(fourth.shortCode(), second.shortCode(), first.shortCode()));

    // Act
    Response response = requestRanking(session.accessToken());

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    List<String> shortUrls = response.jsonPath().getList("urls.shortUrl", String.class);
    List<String> statuses = response.jsonPath().getList("urls.status", String.class);
    List<Integer> accessCounts = response.jsonPath().getList("urls.accessCount", Integer.class);
    List<String> lastAccessedAt = response.jsonPath().getList("urls.lastAccessedAt", String.class);

    assertThat(shortUrls)
        .containsExactly(
            shortUrl(fourth.shortCode()),
            shortUrl(second.shortCode()),
            shortUrl(first.shortCode()));
    assertThat(statuses)
        .containsExactly(UrlStatus.ACTIVE.name(), UrlStatus.ACTIVE.name(), UrlStatus.ACTIVE.name());
    assertThat(accessCounts).containsExactly(12, 9, 5);
    assertThat(lastAccessedAt)
        .containsExactly(
            fourthAccessedAt.toString(), secondAccessedAt.toString(), firstAccessedAt.toString());
    assertThat(response.jsonPath().getList("urls.originalUrl", String.class))
        .containsExactly(fourth.originalUrl(), second.originalUrl(), first.originalUrl());
  }

  @Test
  @DisplayName("Deve retornar 200 com até dez URLs quando rankingSize for 10")
  void shouldReturnRankingWithSizeTen() {
    // Arrange
    String email = "ranking-size-ten-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-ranking-size-ten-url");

    CreatedUrl first =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-size-ten-1",
            "create-ranking-size-ten-1");
    CreatedUrl second =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-size-ten-2",
            "create-ranking-size-ten-2");
    CreatedUrl third =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-size-ten-3",
            "create-ranking-size-ten-3");
    CreatedUrl fourth =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-size-ten-4",
            "create-ranking-size-ten-4");
    urlRepositoryPort.incrementAccessCount(
        first.shortCode(), 1, Instant.parse("2026-06-02T10:00:00Z"));
    urlRepositoryPort.incrementAccessCount(
        second.shortCode(), 3, Instant.parse("2026-06-02T10:05:00Z"));
    urlRepositoryPort.incrementAccessCount(
        third.shortCode(), 8, Instant.parse("2026-06-02T10:10:00Z"));
    urlRepositoryPort.incrementAccessCount(
        fourth.shortCode(), 5, Instant.parse("2026-06-02T10:15:00Z"));
    awaitRankingShortCodes(
        session.accessToken(),
        10,
        List.of(third.shortCode(), fourth.shortCode(), second.shortCode(), first.shortCode()));

    // Act
    Response response = requestRanking(session.accessToken(), 10);

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    assertThat(response.jsonPath().getList("urls.shortUrl", String.class))
        .containsExactly(
            shortUrl(third.shortCode()),
            shortUrl(fourth.shortCode()),
            shortUrl(second.shortCode()),
            shortUrl(first.shortCode()));
    assertThat(response.jsonPath().getList("urls.accessCount", Integer.class))
        .containsExactly(8, 5, 3, 1);
  }

  @Test
  @DisplayName("Deve retornar 200 com lista vazia quando usuário não tiver URLs")
  void shouldReturnEmptyRankingWhenUserHasNoUrls() {
    // Arrange
    String email = "ranking-empty-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-ranking-empty-url");

    // Act
    Response response = requestRanking(session.accessToken());

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    assertThat(response.jsonPath().getList("urls")).isEmpty();
    assertThat(urlTable.scan().items()).isEmpty();
  }

  @Test
  @DisplayName("Deve retornar 200 sem incluir URLs de outros usuários")
  void shouldNotIncludeOtherUserUrlsInRanking() {
    // Arrange
    String ownerEmail = "ranking-owner-url.integration@example.com";
    String otherEmail = "ranking-other-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createActiveUser(otherEmail, PASSWORD);
    AuthenticatedSession ownerSession = login(ownerEmail, PASSWORD, "login-ranking-owner-url");
    AuthenticatedSession otherSession = login(otherEmail, PASSWORD, "login-ranking-other-url");

    CreatedUrl ownerUrl =
        createUrl(
            ownerSession.accessToken(),
            "https://example.com/ranking-owner",
            "create-ranking-owner");
    CreatedUrl otherUrl =
        createUrl(
            otherSession.accessToken(),
            "https://example.com/ranking-other",
            "create-ranking-other");
    urlRepositoryPort.incrementAccessCount(
        ownerUrl.shortCode(), 4, Instant.parse("2026-06-03T10:00:00Z"));
    urlRepositoryPort.incrementAccessCount(
        otherUrl.shortCode(), 99, Instant.parse("2026-06-03T10:05:00Z"));
    awaitRankingShortCodes(ownerSession.accessToken(), null, List.of(ownerUrl.shortCode()));

    // Act
    Response response = requestRanking(ownerSession.accessToken());

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    assertThat(response.jsonPath().getList("urls.shortUrl", String.class))
        .containsExactly(shortUrl(ownerUrl.shortCode()));
    assertThat(response.jsonPath().getList("urls.shortUrl", String.class))
        .doesNotContain(shortUrl(otherUrl.shortCode()));
    assertThat(findByShortCode(ownerUrl.shortCode()).getUserId()).isEqualTo(owner.getId());
  }

  @Test
  @DisplayName("Deve retornar 200 sem incluir URLs deletadas")
  void shouldNotIncludeDeletedUrlsInRanking() {
    // Arrange
    String email = "ranking-deleted-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-ranking-deleted-url");

    CreatedUrl activeUrl =
        createUrl(
            session.accessToken(), "https://example.com/ranking-active", "create-ranking-active");
    CreatedUrl deletedUrl =
        createUrl(
            session.accessToken(), "https://example.com/ranking-deleted", "create-ranking-deleted");
    urlRepositoryPort.incrementAccessCount(
        activeUrl.shortCode(), 5, Instant.parse("2026-06-04T10:00:00Z"));
    urlRepositoryPort.incrementAccessCount(
        deletedUrl.shortCode(), 20, Instant.parse("2026-06-04T10:05:00Z"));
    deleteUrl(session.accessToken(), deletedUrl.shortCode());
    awaitRankingShortCodes(session.accessToken(), null, List.of(activeUrl.shortCode()));

    // Act
    Response response = requestRanking(session.accessToken());

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    assertThat(response.jsonPath().getList("urls.shortUrl", String.class))
        .containsExactly(shortUrl(activeUrl.shortCode()));
    assertThat(response.jsonPath().getList("urls.shortUrl", String.class))
        .doesNotContain(shortUrl(deletedUrl.shortCode()));
    assertThat(findByShortCode(deletedUrl.shortCode()).getStatus()).isEqualTo(UrlStatus.DELETED);
  }

  @Test
  @DisplayName("Deve retornar 200 incluindo URL ativa sem acessos")
  void shouldIncludeActiveUrlWithZeroAccessesWhenRankingHasCapacity() {
    // Arrange
    String email = "ranking-zero-access-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-ranking-zero-access-url");
    CreatedUrl url =
        createUrl(
            session.accessToken(),
            "https://example.com/ranking-zero-access",
            "create-ranking-zero-access");
    awaitRankingShortCodes(session.accessToken(), null, List.of(url.shortCode()));

    // Act
    Response response = requestRanking(session.accessToken());

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    assertThat(response.jsonPath().getList("urls.shortUrl", String.class))
        .containsExactly(shortUrl(url.shortCode()));
    assertThat(response.jsonPath().getList("urls.accessCount", Integer.class)).containsExactly(0);
    assertThat(response.jsonPath().getList("urls.lastAccessedAt")).containsExactly((Object) null);
  }

  @Test
  @DisplayName("Deve retornar 401 quando token estiver ausente")
  void shouldReturnUnauthorizedWhenAccessTokenIsMissing() {
    // Arrange

    // Act
    Response response = given().when().get(RANKING_ENDPOINT);

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
            .get(RANKING_ENDPOINT);

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
  }

  @Test
  @DisplayName("Deve retornar 403 quando usuário autenticado não tiver permissão de ranking")
  void shouldReturnForbiddenWhenAuthenticatedUserDoesNotHaveRankingAuthority() {
    // Arrange
    UserEntity user =
        userTestDataFactory.createActiveUser(
            "ranking-without-authority-url.integration@example.com", PASSWORD);
    String accessTokenWithoutRankingAuthority =
        jwtTokenService.generateAccessToken(
            new JwtAccessTokenSubject(user.getId(), PlanType.FREE.name(), List.of("url:list:own")));

    // Act
    Response response = requestRanking(accessTokenWithoutRankingAuthority);

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

    return new CreatedUrl(
        response.path("shortCode"), response.path("originalUrl"), response.path("createdAt"));
  }

  private void deleteUrl(String accessToken, String shortCode) {
    given()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .when()
        .delete(URLS_ENDPOINT + "/" + shortCode)
        .then()
        .log()
        .ifValidationFails()
        .statusCode(204);
  }

  private void awaitRankingShortCodes(
      String accessToken, Integer rankingSize, List<String> expectedShortCodes) {
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              Response response = requestRanking(accessToken, rankingSize);
              response.then().statusCode(200);
              assertThat(rankingShortCodes(response)).containsExactlyElementsOf(expectedShortCodes);
            });
  }

  private List<String> rankingShortCodes(Response response) {
    return response.jsonPath().getList("urls.shortUrl", String.class).stream()
        .map(this::shortCodeFromShortUrl)
        .toList();
  }

  private Response requestRanking(String accessToken) {
    return requestRanking(accessToken, null);
  }

  private Response requestRanking(String accessToken, Integer rankingSize) {
    var request = given().header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

    if (rankingSize != null) {
      request.queryParam("rankingSize", rankingSize);
    }

    return request.when().get(RANKING_ENDPOINT);
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

  private String shortUrl(String shortCode) {
    return applicationProperties.baseUrl() + "/r/" + shortCode;
  }

  private String shortCodeFromShortUrl(String shortUrl) {
    return shortUrl.substring(shortUrl.lastIndexOf('/') + 1);
  }

  private record CreatedUrl(String shortCode, String originalUrl, String createdAt) {}
}
