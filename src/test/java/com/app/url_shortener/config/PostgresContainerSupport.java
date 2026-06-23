package com.app.url_shortener.config;

import static org.testcontainers.utility.DockerImageName.parse;

import java.sql.DriverManager;
import java.sql.SQLException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

public final class PostgresContainerSupport {

  private static final DockerImageName POSTGRES_IMAGE = parse("postgres:16.3-alpine");
  private static final String DATABASE_NAME = "url_shortener_test";
  private static final String USERNAME = "postgres";
  private static final String PASSWORD = "postgres";

  private static final GenericContainer<?> POSTGRES_CONTAINER =
      new GenericContainer<>(POSTGRES_IMAGE)
          .withEnv("POSTGRES_DB", DATABASE_NAME)
          .withEnv("POSTGRES_USER", USERNAME)
          .withEnv("POSTGRES_PASSWORD", PASSWORD)
          .withExposedPorts(5432)
          .waitingFor(Wait.forLogMessage(".*database system is ready to accept connections.*\\n", 1));

  static {
    POSTGRES_CONTAINER.start();
  }

  private PostgresContainerSupport() {}

  public static void registerDatasourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", PostgresContainerSupport::jdbcUrl);
    registry.add("spring.datasource.username", () -> USERNAME);
    registry.add("spring.datasource.password", () -> PASSWORD);
    registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    registry.add("spring.jpa.properties.hibernate.jdbc.time_zone", () -> "UTC");
    registry.add("spring.flyway.enabled", () -> "true");
  }

  public static void resetDatabase() {
    try (var connection = DriverManager.getConnection(jdbcUrl(), USERNAME, PASSWORD);
        var statement = connection.createStatement()) {
      statement.executeUpdate(
          """
          TRUNCATE TABLE
              email_dispatches,
              email_verification_tokens,
              refresh_tokens,
              user_roles,
              users,
              outbox_events
          RESTART IDENTITY
          """);
    } catch (SQLException exception) {
      throw new IllegalStateException("Failed to reset PostgreSQL test database", exception);
    }
  }

  private static String jdbcUrl() {
    return String.format(
        "jdbc:postgresql://%s:%d/%s",
        POSTGRES_CONTAINER.getHost(),
        POSTGRES_CONTAINER.getMappedPort(5432),
        DATABASE_NAME);
  }
}
