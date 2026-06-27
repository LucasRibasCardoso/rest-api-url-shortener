package com.app.url_shortener.url.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.security.jwt.JwtAccessTokenSubject;
import com.app.url_shortener.security.jwt.JwtTokenService;
import com.app.url_shortener.shared.config.ApplicationProperties;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.url.domain.exception.UrlErrorCode;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@DisplayName("Testes de Integração - Listagem das próprias URLs")
class UrlListMyUrlsIT extends AbstractIntegrationTest {

  private static final String URLS_ENDPOINT = "/api/v1/urls";
  private static final String MY_URLS_ENDPOINT = URLS_ENDPOINT + "/me";
  private static final String PASSWORD = "secure-password";

  private final UserTestDataFactory userTestDataFactory;
  private final DynamoDbTable<UrlEntity> urlTable;
  private final ApplicationProperties applicationProperties;
  private final JwtTokenService jwtTokenService;

  @Autowired
  UrlListMyUrlsIT(
      UserTestDataFactory userTestDataFactory,
      DynamoDbTable<UrlEntity> urlTable,
      ApplicationProperties applicationProperties,
      JwtTokenService jwtTokenService) {
    this.userTestDataFactory = userTestDataFactory;
    this.urlTable = urlTable;
    this.applicationProperties = applicationProperties;
    this.jwtTokenService = jwtTokenService;
  }

  @Test
  @DisplayName("Deve retornar 200 com URLs ativas do usuário autenticado")
  void shouldReturnAuthenticatedUserActiveUrls() {
    // Arrange
    String ownerEmail = "my-list-owner-url.integration@example.com";
    String otherEmail = "my-list-other-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createActiveUser(otherEmail, PASSWORD);
    AuthenticatedSession ownerSession = login(ownerEmail, PASSWORD, "login-my-list-owner-url");
    AuthenticatedSession otherSession = login(otherEmail, PASSWORD, "login-my-list-other-url");

    CreatedUrl ownerFirst =
        createUrl(
            ownerSession.accessToken(),
            "https://example.com/my-list-owner-1",
            "create-my-list-owner-1");
    CreatedUrl ownerSecond =
        createUrl(
            ownerSession.accessToken(),
            "https://example.com/my-list-owner-2",
            "create-my-list-owner-2");
    CreatedUrl otherUrl =
        createUrl(
            otherSession.accessToken(),
            "https://example.com/my-list-other",
            "create-my-list-other");

    // Act
    Response response = requestMyUrls(ownerSession.accessToken());

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("nextCursor", is((String) null));

    List<String> shortCodes = response.jsonPath().getList("urls.shortCode", String.class);
    List<String> statuses = response.jsonPath().getList("urls.status", String.class);
    List<String> shortUrls = response.jsonPath().getList("urls.shortUrl", String.class);

    assertThat(shortCodes)
        .containsExactlyInAnyOrder(ownerFirst.shortCode(), ownerSecond.shortCode());
    assertThat(shortCodes).doesNotContain(otherUrl.shortCode());
    assertThat(statuses).containsOnly(UrlStatus.ACTIVE.name());
    assertThat(shortUrls)
        .containsExactlyInAnyOrder(
            shortUrl(ownerFirst.shortCode()), shortUrl(ownerSecond.shortCode()));
    assertThat(findByShortCode(ownerFirst.shortCode()).getUserId()).isEqualTo(owner.getId());
  }

  @Test
  @DisplayName("Deve retornar 200 com lista vazia quando usuário autenticado não tiver URLs")
  void shouldReturnEmptyPageWhenAuthenticatedUserHasNoUrls() {
    // Arrange
    String email = "my-list-empty-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-my-list-empty-url");

    // Act
    Response response = requestMyUrls(session.accessToken());

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("nextCursor", is((String) null));

    assertThat(response.jsonPath().getList("urls")).isEmpty();
    assertThat(urlTable.scan().items()).isEmpty();
  }

  @Test
  @DisplayName("Deve retornar 200 com URLs deletadas quando filtro for DELETED")
  void shouldReturnDeletedUrlsWhenStatusFilterIsDeleted() {
    // Arrange
    String email = "my-list-deleted-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-my-list-deleted-url");

    CreatedUrl activeUrl =
        createUrl(
            session.accessToken(), "https://example.com/my-list-active", "create-my-list-active");
    CreatedUrl deletedUrl =
        createUrl(
            session.accessToken(), "https://example.com/my-list-deleted", "create-my-list-deleted");
    deleteUrl(session.accessToken(), deletedUrl.shortCode());

    // Act
    Response response = requestMyUrls(session.accessToken(), null, "DELETED", null);

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    List<String> shortCodes = response.jsonPath().getList("urls.shortCode", String.class);
    List<String> statuses = response.jsonPath().getList("urls.status", String.class);

    assertThat(shortCodes).containsExactly(deletedUrl.shortCode());
    assertThat(shortCodes).doesNotContain(activeUrl.shortCode());
    assertThat(statuses).containsExactly(UrlStatus.DELETED.name());
    assertThat(findByShortCode(deletedUrl.shortCode()).getStatus()).isEqualTo(UrlStatus.DELETED);
  }

  @Test
  @DisplayName("Deve retornar 200 com URLs ativas e deletadas quando filtro for ALL")
  void shouldReturnActiveAndDeletedUrlsWhenStatusFilterIsAll() {
    // Arrange
    String email = "my-list-all-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-my-list-all-url");

    CreatedUrl activeUrl =
        createUrl(
            session.accessToken(),
            "https://example.com/my-list-all-active",
            "create-my-list-all-active");
    CreatedUrl deletedUrl =
        createUrl(
            session.accessToken(),
            "https://example.com/my-list-all-deleted",
            "create-my-list-all-deleted");
    deleteUrl(session.accessToken(), deletedUrl.shortCode());

    // Act
    Response response = requestMyUrls(session.accessToken(), null, "ALL", null);

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    List<String> shortCodes = response.jsonPath().getList("urls.shortCode", String.class);
    List<String> statuses = response.jsonPath().getList("urls.status", String.class);

    assertThat(shortCodes).containsExactlyInAnyOrder(activeUrl.shortCode(), deletedUrl.shortCode());
    assertThat(statuses).contains(UrlStatus.ACTIVE.name(), UrlStatus.DELETED.name());
  }

  @Test
  @DisplayName("Deve retornar 200 com próxima página ao usar cursor")
  void shouldReturnNextPageWhenCursorIsProvided() {
    // Arrange
    String email = "my-list-pagination-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-my-list-pagination-url");

    CreatedUrl first =
        createUrl(
            session.accessToken(), "https://example.com/my-list-page-1", "create-my-list-page-1");
    CreatedUrl second =
        createUrl(
            session.accessToken(), "https://example.com/my-list-page-2", "create-my-list-page-2");
    CreatedUrl third =
        createUrl(
            session.accessToken(), "https://example.com/my-list-page-3", "create-my-list-page-3");

    Response firstPage =
        requestMyUrls(session.accessToken(), 2, "ACTIVE", null)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("nextCursor", notNullValue())
            .extract()
            .response();
    String nextCursor = firstPage.path("nextCursor");

    // Act
    Response secondPage = requestMyUrls(session.accessToken(), 2, "ACTIVE", nextCursor);

    // Assert
    secondPage
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("nextCursor", is((String) null));

    List<String> firstPageShortCodes = firstPage.jsonPath().getList("urls.shortCode", String.class);
    List<String> secondPageShortCodes =
        secondPage.jsonPath().getList("urls.shortCode", String.class);
    var allReturnedShortCodes = new HashSet<String>();
    allReturnedShortCodes.addAll(firstPageShortCodes);
    allReturnedShortCodes.addAll(secondPageShortCodes);

    assertThat(firstPageShortCodes).hasSize(2);
    assertThat(secondPageShortCodes).hasSize(1);
    assertThat(allReturnedShortCodes)
        .containsExactlyInAnyOrder(first.shortCode(), second.shortCode(), third.shortCode());
  }

  @Test
  @DisplayName("Deve retornar 200 com URLs ordenadas da mais recente para a mais antiga")
  void shouldReturnUrlsOrderedFromNewestToOldest() {
    // Arrange
    String email = "my-list-order-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-my-list-order-url");

    CreatedUrl first =
        createUrl(
            session.accessToken(), "https://example.com/my-list-order-1", "create-my-list-order-1");
    CreatedUrl second =
        createUrl(
            session.accessToken(), "https://example.com/my-list-order-2", "create-my-list-order-2");
    CreatedUrl third =
        createUrl(
            session.accessToken(), "https://example.com/my-list-order-3", "create-my-list-order-3");

    // Act
    Response response = requestMyUrls(session.accessToken());

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    List<String> shortCodes = response.jsonPath().getList("urls.shortCode", String.class);
    assertThat(shortCodes)
        .containsExactly(third.shortCode(), second.shortCode(), first.shortCode());
  }

  @Test
  @DisplayName("Deve retornar 400 quando cursor for inválido")
  void shouldReturnBadRequestWhenCursorIsInvalid() {
    // Arrange
    String email = "my-list-invalid-cursor-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-my-list-invalid-cursor-url");

    // Act
    Response response = requestMyUrls(session.accessToken(), null, "ACTIVE", "invalid");

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(400)
        .contentType("application/problem+json")
        .body("title", is("Validação"))
        .body("type", is(ProblemType.VALIDATION))
        .body("detail", is(UrlErrorCode.URL_CURSOR_INVALID.getMessage()))
        .body("errorCode", is(UrlErrorCode.URL_CURSOR_INVALID.getCode()));
  }

  @Test
  @DisplayName("Deve retornar 401 quando token estiver ausente")
  void shouldReturnUnauthorizedWhenAccessTokenIsMissing() {
    // Arrange

    // Act
    Response response = given().when().get(MY_URLS_ENDPOINT);

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
            .get(MY_URLS_ENDPOINT);

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
  @DisplayName(
      "Deve retornar 403 quando usuário autenticado não tiver permissão de listagem própria")
  void shouldReturnForbiddenWhenAuthenticatedUserDoesNotHaveListOwnAuthority() {
    // Arrange
    UserEntity user =
        userTestDataFactory.createActiveUser(
            "my-list-without-authority-url.integration@example.com", PASSWORD);
    String accessTokenWithoutListOwnAuthority =
        jwtTokenService.generateAccessToken(
            new JwtAccessTokenSubject(user.getId(), PlanType.FREE.name(), List.of("url:read:own")));

    // Act
    Response response = requestMyUrls(accessTokenWithoutListOwnAuthority);

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

  private Response requestMyUrls(String accessToken) {
    return requestMyUrls(accessToken, null, null, null);
  }

  private Response requestMyUrls(String accessToken, Integer limit, String status, String cursor) {
    var request = given().header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);

    if (limit != null) {
      request.queryParam("limit", limit);
    }

    if (status != null) {
      request.queryParam("status", status);
    }

    if (cursor != null) {
      request.queryParam("cursor", cursor);
    }

    return request.when().get(MY_URLS_ENDPOINT);
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

  private record CreatedUrl(String shortCode, String originalUrl, String createdAt) {}
}
