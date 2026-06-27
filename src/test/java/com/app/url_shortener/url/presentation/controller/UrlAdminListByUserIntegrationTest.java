package com.app.url_shortener.url.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
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
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@DisplayName("Testes de Integração - Listagem administrativa de URLs por usuário")
class UrlAdminListByUserIntegrationTest extends AbstractIntegrationTest {

  private static final String URLS_ENDPOINT = "/api/v1/urls";
  private static final String PASSWORD = "secure-password";

  private final UserTestDataFactory userTestDataFactory;
  private final DynamoDbTable<UrlEntity> urlTable;
  private final ApplicationProperties applicationProperties;

  @Autowired
  UrlAdminListByUserIntegrationTest(
      UserTestDataFactory userTestDataFactory,
      DynamoDbTable<UrlEntity> urlTable,
      ApplicationProperties applicationProperties) {
    this.userTestDataFactory = userTestDataFactory;
    this.urlTable = urlTable;
    this.applicationProperties = applicationProperties;
  }

  @Test
  @DisplayName("Deve retornar 200 com URLs ativas do usuário informado para administrador")
  void shouldReturnActiveUrlsForRequestedUserWhenRequesterIsAdmin() {
    // Arrange
    String ownerEmail = "admin-list-owner-url.integration@example.com";
    String otherEmail = "admin-list-other-url.integration@example.com";
    String adminEmail = "admin-list-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createActiveUser(otherEmail, PASSWORD);
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession ownerSession = login(ownerEmail, PASSWORD, "login-admin-list-owner-url");
    AuthenticatedSession otherSession = login(otherEmail, PASSWORD, "login-admin-list-other-url");
    AuthenticatedSession adminSession = login(adminEmail, PASSWORD, "login-admin-list-admin-url");

    CreatedUrl ownerFirst =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-owner-1", "create-admin-list-owner-1");
    CreatedUrl ownerSecond =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-owner-2", "create-admin-list-owner-2");
    CreatedUrl otherUrl =
        createUrl(otherSession.accessToken(), "https://example.com/admin-list-other", "create-admin-list-other");

    // Act
    Response response = requestAdminList(adminSession.accessToken(), owner.getId());

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

    assertThat(shortCodes).containsExactlyInAnyOrder(ownerFirst.shortCode(), ownerSecond.shortCode());
    assertThat(shortCodes).doesNotContain(otherUrl.shortCode());
    assertThat(statuses).containsOnly(UrlStatus.ACTIVE.name());
    assertThat(shortUrls)
        .containsExactlyInAnyOrder(
            shortUrl(ownerFirst.shortCode()),
            shortUrl(ownerSecond.shortCode()));
    assertThat(findByShortCode(ownerFirst.shortCode()).getUserId()).isEqualTo(owner.getId());
  }

  @Test
  @DisplayName("Deve retornar 200 com lista vazia quando usuário existente não tiver URLs")
  void shouldReturnEmptyPageWhenExistingUserHasNoUrls() {
    // Arrange
    String ownerEmail = "admin-list-empty-owner-url.integration@example.com";
    String adminEmail = "admin-list-empty-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession adminSession = login(adminEmail, PASSWORD, "login-admin-list-empty-admin-url");

    // Act
    Response response = requestAdminList(adminSession.accessToken(), owner.getId());

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
  @DisplayName("Deve retornar 200 com lista vazia quando UUID do usuário não existir")
  void shouldReturnEmptyPageWhenUserIdDoesNotExist() {
    // Arrange
    String adminEmail = "admin-list-missing-user-admin-url.integration@example.com";
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession adminSession =
        login(adminEmail, PASSWORD, "login-admin-list-missing-user-admin-url");
    UUID missingUserId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac099");

    // Act
    Response response = requestAdminList(adminSession.accessToken(), missingUserId);

    // Assert
    response
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("nextCursor", is((String) null));

    assertThat(response.jsonPath().getList("urls")).isEmpty();
  }

  @Test
  @DisplayName("Deve retornar 200 com URLs deletadas quando filtro for DELETED")
  void shouldReturnDeletedUrlsWhenStatusFilterIsDeleted() {
    // Arrange
    String ownerEmail = "admin-list-deleted-owner-url.integration@example.com";
    String adminEmail = "admin-list-deleted-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession ownerSession = login(ownerEmail, PASSWORD, "login-admin-list-deleted-owner-url");
    AuthenticatedSession adminSession = login(adminEmail, PASSWORD, "login-admin-list-deleted-admin-url");

    CreatedUrl activeUrl =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-active", "create-admin-list-active");
    CreatedUrl deletedUrl =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-deleted", "create-admin-list-deleted");
    deleteUrl(ownerSession.accessToken(), deletedUrl.shortCode());

    // Act
    Response response = requestAdminList(adminSession.accessToken(), owner.getId(), null, "DELETED", null);

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
    String ownerEmail = "admin-list-all-owner-url.integration@example.com";
    String adminEmail = "admin-list-all-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession ownerSession = login(ownerEmail, PASSWORD, "login-admin-list-all-owner-url");
    AuthenticatedSession adminSession = login(adminEmail, PASSWORD, "login-admin-list-all-admin-url");

    CreatedUrl activeUrl =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-all-active", "create-admin-list-all-active");
    CreatedUrl deletedUrl =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-all-deleted", "create-admin-list-all-deleted");
    deleteUrl(ownerSession.accessToken(), deletedUrl.shortCode());

    // Act
    Response response = requestAdminList(adminSession.accessToken(), owner.getId(), null, "ALL", null);

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
    String ownerEmail = "admin-list-pagination-owner-url.integration@example.com";
    String adminEmail = "admin-list-pagination-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession ownerSession =
        login(ownerEmail, PASSWORD, "login-admin-list-pagination-owner-url");
    AuthenticatedSession adminSession =
        login(adminEmail, PASSWORD, "login-admin-list-pagination-admin-url");

    CreatedUrl first =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-page-1", "create-admin-list-page-1");
    CreatedUrl second =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-page-2", "create-admin-list-page-2");
    CreatedUrl third =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-page-3", "create-admin-list-page-3");

    Response firstPage =
        requestAdminList(adminSession.accessToken(), owner.getId(), 2, "ACTIVE", null)
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
    Response secondPage = requestAdminList(adminSession.accessToken(), owner.getId(), 2, "ACTIVE", nextCursor);

    // Assert
    secondPage
        .then()
        .log()
        .ifValidationFails()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("nextCursor", is((String) null));

    List<String> firstPageShortCodes = firstPage.jsonPath().getList("urls.shortCode", String.class);
    List<String> secondPageShortCodes = secondPage.jsonPath().getList("urls.shortCode", String.class);
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
    String ownerEmail = "admin-list-order-owner-url.integration@example.com";
    String adminEmail = "admin-list-order-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession ownerSession = login(ownerEmail, PASSWORD, "login-admin-list-order-owner-url");
    AuthenticatedSession adminSession = login(adminEmail, PASSWORD, "login-admin-list-order-admin-url");

    CreatedUrl first =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-order-1", "create-admin-list-order-1");
    CreatedUrl second =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-order-2", "create-admin-list-order-2");
    CreatedUrl third =
        createUrl(ownerSession.accessToken(), "https://example.com/admin-list-order-3", "create-admin-list-order-3");

    // Act
    Response response = requestAdminList(adminSession.accessToken(), owner.getId());

    // Assert
    response.then().log().ifValidationFails().statusCode(200).contentType(ContentType.JSON);

    List<String> shortCodes = response.jsonPath().getList("urls.shortCode", String.class);
    assertThat(shortCodes).containsExactly(third.shortCode(), second.shortCode(), first.shortCode());
  }

  @Test
  @DisplayName("Deve retornar 400 quando cursor for inválido")
  void shouldReturnBadRequestWhenCursorIsInvalid() {
    // Arrange
    String adminEmail = "admin-list-invalid-cursor-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser("admin-list-invalid-cursor-owner-url.integration@example.com", PASSWORD);
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession adminSession =
        login(adminEmail, PASSWORD, "login-admin-list-invalid-cursor-admin-url");

    // Act
    Response response = requestAdminList(adminSession.accessToken(), owner.getId(), null, "ACTIVE", "invalid");

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
    UUID userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac101");

    // Act
    Response response = given().when().get(adminListEndpoint(userId));

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
    UUID userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac102");

    // Act
    Response response =
        given()
            .header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token")
            .when()
            .get(adminListEndpoint(userId));

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
  @DisplayName("Deve retornar 403 quando usuário comum tentar listar URLs de outro usuário")
  void shouldReturnForbiddenWhenRequesterIsNotAdmin() {
    // Arrange
    String requesterEmail = "admin-list-forbidden-requester-url.integration@example.com";
    UserEntity targetUser =
        userTestDataFactory.createActiveUser("admin-list-forbidden-target-url.integration@example.com", PASSWORD);
    userTestDataFactory.createActiveUser(requesterEmail, PASSWORD);
    AuthenticatedSession requesterSession =
        login(requesterEmail, PASSWORD, "login-admin-list-forbidden-requester-url");

    // Act
    Response response = requestAdminList(requesterSession.accessToken(), targetUser.getId());

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
        response.path("shortCode"),
        response.path("originalUrl"),
        response.path("createdAt"));
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

  private Response requestAdminList(String accessToken, UUID userId) {
    return requestAdminList(accessToken, userId, null, null, null);
  }

  private Response requestAdminList(
      String accessToken,
      UUID userId,
      Integer limit,
      String status,
      String cursor) {
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

    return request.when().get(adminListEndpoint(userId));
  }

  private static String requestBody(String originalUrl) {
    return """
        {
          "originalUrl": "%s"
        }
        """
        .formatted(originalUrl);
  }

  private static String adminListEndpoint(UUID userId) {
    return URLS_ENDPOINT + "/users/" + userId;
  }

  private UrlEntity findByShortCode(String shortCode) {
    return urlTable.getItem(Key.builder().partitionValue(shortCode).build());
  }

  private String shortUrl(String shortCode) {
    return applicationProperties.baseUrl() + "/r/" + shortCode;
  }

  private record CreatedUrl(String shortCode, String originalUrl, String createdAt) {}
}
