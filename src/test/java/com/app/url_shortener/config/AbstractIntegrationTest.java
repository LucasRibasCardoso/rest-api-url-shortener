package com.app.url_shortener.config;

import io.restassured.RestAssured;
import io.restassured.config.RestAssuredConfig;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static io.restassured.config.RedirectConfig.redirectConfig;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

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
}
