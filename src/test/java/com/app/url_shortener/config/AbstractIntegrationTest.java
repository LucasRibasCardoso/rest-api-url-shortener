package com.app.url_shortener.config;

import static io.restassured.RestAssured.given;
import static io.restassured.config.RedirectConfig.redirectConfig;
import static org.hamcrest.Matchers.blankOrNullString;
import static org.hamcrest.Matchers.not;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.http.HttpHeaders;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

  private static final String LOGIN_ENDPOINT = "/api/v1/auth/login";

  @LocalServerPort
  private int port;

  @BeforeEach
  void setupTest() {
    PostgresContainerSupport.resetDatabase();
    RedisContainerSupport.resetRedis();
    LocalStackContainerSupport.resetDynamoDbTables();
    LocalStackContainerSupport.resetSqsQueues();
    LocalStackContainerSupport.resetSesMessages();
    RestAssured.port = this.port;
    RestAssured.config = RestAssuredConfig.config().redirect(redirectConfig().followRedirects(false));
  }

  @DynamicPropertySource
  static void registerDynamicProperties(DynamicPropertyRegistry registry) {
    PostgresContainerSupport.registerDatasourceProperties(registry);
    RedisContainerSupport.registerRedisProperties(registry);
    LocalStackContainerSupport.registerDynamoDbProperties(registry);
    LocalStackContainerSupport.registerSQSProperties(registry);
    LocalStackContainerSupport.registerSesProperties(registry);
  }

  @BeforeAll
  static void setupLocalStackResources() {
    LocalStackContainerSupport.setupDynamoDbTables();
    LocalStackContainerSupport.setupSqsQueues();
    LocalStackContainerSupport.setupSesIdentity();
  }

  protected AuthenticatedSession login(String email, String password, String idempotencyKey) {
    var requestBody =
        """
        {
          "email": "%s",
          "password": "%s"
        }
        """
        .formatted(email, password);

    var response =
        given()
            .contentType(ContentType.JSON)
            .header("Idempotency-Key", idempotencyKey)
            .body(requestBody)
            .when()
            .post(LOGIN_ENDPOINT)
            .then()
            .statusCode(200)
            .contentType(ContentType.JSON)
            .header(HttpHeaders.SET_COOKIE, not(blankOrNullString()))
            .body("accessToken", not(blankOrNullString()))
            .extract()
            .response();

    return new AuthenticatedSession(response.path("accessToken"), response.cookie("refreshToken"));
  }

  protected record AuthenticatedSession(String accessToken, String refreshToken) {}
}
