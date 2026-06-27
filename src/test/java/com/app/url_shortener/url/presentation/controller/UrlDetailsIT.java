package com.app.url_shortener.url.presentation.controller;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import com.app.url_shortener.config.AbstractIntegrationTest;
import com.app.url_shortener.config.UserTestDataFactory;
import com.app.url_shortener.iam.infrastructure.entity.UserEntity;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.url.domain.exception.UrlErrorCode;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

@DisplayName("Testes de Integração - Detalhes de URL encurtada")
class UrlDetailsIT extends AbstractIntegrationTest {

  private static final String URLS_ENDPOINT = "/api/v1/urls";
  private static final String PASSWORD = "secure-password";

  private final UserTestDataFactory userTestDataFactory;
  private final DynamoDbTable<UrlEntity> urlTable;

  @Autowired
  UrlDetailsIT(UserTestDataFactory userTestDataFactory, DynamoDbTable<UrlEntity> urlTable) {
    this.userTestDataFactory = userTestDataFactory;
    this.urlTable = urlTable;
  }

  @Test
  @DisplayName("Deve retornar 200 com detalhes da URL quando o usuário for o dono")
  void shouldReturnUrlDetailsWhenRequesterIsOwner() {
    // Arrange
    String email = "details-owner-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-details-owner-url");
    Response createResponse =
        requestCreateUrl(
                session.accessToken(),
                requestBody("https://example.com/details-owner"),
                "create-details-owner-url")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .extract()
            .response();
    String shortCode = createResponse.path("shortCode");

    // Act
    Response response =
        requestFindUrlDetails(session.accessToken(), shortCode)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("shortCode", is(shortCode))
            .body("originalUrl", is("https://example.com/details-owner"))
            .body("userId", is(user.getId().toString()))
            .body("status", is(UrlStatus.ACTIVE.name()))
            .body("createdAt", not(blankOrNullString()))
            .body("updatedAt", not(blankOrNullString()))
            .body("accessCount", is(0))
            .extract()
            .response();

    // Assert
    UrlEntity persisted = findByShortCode(shortCode);
    assertThat(persisted).isNotNull();
    assertThat(response.path("shortCode").toString()).isEqualTo(persisted.getShortCode());
    assertThat(response.path("originalUrl").toString()).isEqualTo(persisted.getOriginalUrl());
    assertThat(response.path("userId").toString()).isEqualTo(persisted.getUserId().toString());
    assertThat(response.path("status").toString()).isEqualTo(persisted.getStatus().name());
    assertThat(persisted.getAccessCount()).isZero();
    assertThat(persisted.getLastAccessedAt()).isNull();
  }

  @Test
  @DisplayName("Deve retornar 200 com detalhes quando administrador busca URL de outro usuário")
  void shouldReturnDetailsWhenAdminReadsAnyUrl() {
    // Arrange
    String ownerEmail = "details-admin-owner-url.integration@example.com";
    String adminEmail = "details-admin-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createAdminUser(adminEmail, PASSWORD);
    AuthenticatedSession ownerSession =
        login(ownerEmail, PASSWORD, "login-details-admin-owner-url");
    AuthenticatedSession adminSession = login(adminEmail, PASSWORD, "login-details-admin-url");
    Response createResponse =
        requestCreateUrl(
                ownerSession.accessToken(),
                requestBody("https://example.com/details-admin"),
                "create-details-admin-url")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .extract()
            .response();
    String shortCode = createResponse.path("shortCode");

    // Act
    Response response =
        requestFindUrlDetails(adminSession.accessToken(), shortCode)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("shortCode", is(shortCode))
            .body("originalUrl", is("https://example.com/details-admin"))
            .body("userId", is(owner.getId().toString()))
            .body("status", is(UrlStatus.ACTIVE.name()))
            .extract()
            .response();

    // Assert
    UrlEntity persisted = findByShortCode(shortCode);
    assertThat(persisted).isNotNull();
    assertThat(response.path("userId").toString()).isEqualTo(owner.getId().toString());
    assertThat(persisted.getUserId()).isEqualTo(owner.getId());
  }

  @Test
  @DisplayName("Deve retornar 404 quando usuário tenta buscar detalhes de URL de outro usuário")
  void shouldReturnNotFoundWhenRequesterDoesNotOwnUrl() {
    // Arrange
    String ownerEmail = "details-other-owner-url.integration@example.com";
    String requesterEmail = "details-other-requester-url.integration@example.com";
    UserEntity owner = userTestDataFactory.createActiveUser(ownerEmail, PASSWORD);
    userTestDataFactory.createActiveUser(requesterEmail, PASSWORD);
    AuthenticatedSession ownerSession =
        login(ownerEmail, PASSWORD, "login-details-other-owner-url");
    AuthenticatedSession requesterSession =
        login(requesterEmail, PASSWORD, "login-details-other-requester-url");
    Response createResponse =
        requestCreateUrl(
                ownerSession.accessToken(),
                requestBody("https://example.com/details-other-owner"),
                "create-details-other-owner-url")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .extract()
            .response();
    String shortCode = createResponse.path("shortCode");

    // Act
    Response response =
        requestFindUrlDetails(requesterSession.accessToken(), shortCode)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("title", is("Não encontrado"))
            .body("type", is(ProblemType.NOT_FOUND))
            .body("detail", is(UrlErrorCode.URL_NOT_FOUND.getMessage()))
            .body("errorCode", is(UrlErrorCode.URL_NOT_FOUND.getCode()))
            .extract()
            .response();

    // Assert
    assertThat(response.statusCode()).isEqualTo(404);

    UrlEntity persisted = findByShortCode(shortCode);
    assertThat(persisted).isNotNull();
    assertThat(persisted.getUserId()).isEqualTo(owner.getId());
  }

  @Test
  @DisplayName("Deve retornar 404 quando a URL não existir")
  void shouldReturnNotFoundWhenShortCodeDoesNotExist() {
    // Arrange
    String email = "details-missing-url.integration@example.com";
    userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-details-missing-url");

    // Act
    Response response =
        requestFindUrlDetails(session.accessToken(), "Missing123")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(404)
            .contentType("application/problem+json")
            .body("title", is("Não encontrado"))
            .body("type", is(ProblemType.NOT_FOUND))
            .body("detail", is(UrlErrorCode.URL_NOT_FOUND.getMessage()))
            .body("errorCode", is(UrlErrorCode.URL_NOT_FOUND.getCode()))
            .extract()
            .response();

    // Assert
    assertThat(response.statusCode()).isEqualTo(404);
    assertThat(urlTable.scan().items()).isEmpty();
  }

  @Test
  @DisplayName("Deve retornar 401 quando token estiver ausente")
  void shouldReturnUnauthorizedWhenAccessTokenIsMissing() {
    // Arrange

    // Act
    Response response = given().when().get(URLS_ENDPOINT + "/Missing123");

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
  @DisplayName("Deve retornar 200 com detalhes de URL deletada quando usuário for o dono")
  void shouldReturnDeletedUrlDetailsWhenRequesterIsOwner() {
    // Arrange
    String email = "details-deleted-url.integration@example.com";
    UserEntity user = userTestDataFactory.createActiveUser(email, PASSWORD);
    AuthenticatedSession session = login(email, PASSWORD, "login-details-deleted-url");
    Response createResponse =
        requestCreateUrl(
                session.accessToken(),
                requestBody("https://example.com/details-deleted"),
                "create-details-deleted-url")
            .then()
            .log()
            .ifValidationFails()
            .statusCode(201)
            .contentType(ContentType.JSON)
            .extract()
            .response();
    String shortCode = createResponse.path("shortCode");

    // Deletar URL
    given()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + session.accessToken())
        .when()
        .delete(URLS_ENDPOINT + "/" + shortCode);

    // Act
    Response response =
        requestFindUrlDetails(session.accessToken(), shortCode)
            .then()
            .log()
            .ifValidationFails()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .body("shortCode", is(shortCode))
            .body("originalUrl", is("https://example.com/details-deleted"))
            .body("userId", is(user.getId().toString()))
            .body("status", is(UrlStatus.DELETED.name()))
            .body("deletedAt", not(blankOrNullString()))
            .body("deletedBy", is(user.getId().toString()))
            .extract()
            .response();

    // Assert
    UrlEntity persisted = findByShortCode(shortCode);
    assertThat(persisted).isNotNull();
    assertThat(persisted.getStatus()).isEqualTo(UrlStatus.DELETED);
    assertThat(persisted.getDeletedAt()).isNotNull();
    assertThat(persisted.getDeletedBy()).isEqualTo(user.getId());
    assertThat(response.path("deletedAt").toString())
        .isEqualTo(persisted.getDeletedAt().toString());
  }

  private Response requestCreateUrl(String accessToken, String requestBody, String idempotencyKey) {
    return given()
        .contentType(ContentType.JSON)
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .header("Idempotency-Key", idempotencyKey)
        .body(requestBody)
        .when()
        .post(URLS_ENDPOINT);
  }

  private Response requestFindUrlDetails(String accessToken, String shortCode) {
    return given()
        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
        .when()
        .get(URLS_ENDPOINT + "/" + shortCode);
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
}
