package com.app.url_shortener.shared.database.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.shared.database.PostgresConstraintExtractor;
import java.sql.SQLException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@DisplayName("Testes de Unidade - PostgresConstraintExtractor")
class PostgresConstraintExtractorTest {

  private final PostgresConstraintExtractor extractor = new PostgresConstraintExtractor();

  @Nested
  @DisplayName("Extração de constraint única")
  class ExtractUniqueConstraintNameTests {

    @Test
    @DisplayName("Deve extrair nome de constraint única de exceção do Hibernate")
    void shouldExtractUniqueConstraintNameFromHibernateException() {
      // 1. Arrange
      var sqlException =
          uniqueViolationSqlException(
              "duplicate key value violates unique constraint \"uk_users_email\"");
      var exception =
          new ConstraintViolationException(
              "could not execute statement", sqlException, "uk_users_email");

      // 2. Act
      var result = extractor.extractUniqueConstraintName(exception);

      // 3. Assert
      assertThat(result).contains("uk_users_email");
    }

    @Test
    @DisplayName("Deve extrair nome de constraint única de causa aninhada")
    void shouldExtractUniqueConstraintNameFromNestedCause() {
      // 1. Arrange
      var sqlException =
          uniqueViolationSqlException(
              "duplicate key value violates unique constraint \"uk_users_email\"");
      var constraintException =
          new ConstraintViolationException(
              "could not execute statement", sqlException, "uk_users_email");
      var exception = new RuntimeException("outer exception", constraintException);

      // 2. Act
      var result = extractor.extractUniqueConstraintName(exception);

      // 3. Assert
      assertThat(result).contains("uk_users_email");
    }

    @Test
    @DisplayName("Deve retornar vazio quando SQLState não for violação única")
    void shouldReturnEmptyWhenSqlStateIsNotUniqueViolation() {
      // 1. Arrange
      var sqlException =
          new SQLException(
              "duplicate key value violates unique constraint \"uk_users_email\"", "23503");
      var exception =
          new ConstraintViolationException(
              "could not execute statement", sqlException, "uk_users_email");

      // 2. Act
      var result = extractor.extractUniqueConstraintName(exception);

      // 3. Assert
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio quando constraint não estiver disponível")
    void shouldExtractConstraintNameFromSqlExceptionMessageWhenHibernateNameIsNotAvailable() {
      // 1. Arrange
      var sqlException =
          uniqueViolationSqlException(
              "duplicate key value violates unique constraint \"uk_users_email\"");
      var exception =
          new ConstraintViolationException("could not execute statement", sqlException, null);

      // 2. Act
      var result = extractor.extractUniqueConstraintName(exception);

      // 3. Assert
      assertThat(result).contains("uk_users_email");
    }

    @Test
    @DisplayName("Deve extrair nome de constraint única de SQLException aninhada")
    void shouldExtractUniqueConstraintNameFromNestedSqlException() {
      // 1. Arrange
      var sqlException =
          uniqueViolationSqlException(
              "duplicate key value violates unique constraint \"uk_user_email\"");
      var exception = new RuntimeException("outer exception", sqlException);

      // 2. Act
      var result = extractor.extractUniqueConstraintName(exception);

      // 3. Assert
      assertThat(result).contains("uk_user_email");
    }

    @Test
    @DisplayName("Deve retornar vazio quando mensagem não contiver nome da constraint")
    void shouldReturnEmptyWhenMessageDoesNotContainConstraintName() {
      // 1. Arrange
      var sqlException =
          uniqueViolationSqlException("duplicate key value violates unique constraint");

      // 2. Act
      var result = extractor.extractUniqueConstraintName(sqlException);

      // 3. Assert
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("Deve retornar vazio quando não houver violação de constraint")
    void shouldReturnEmptyWhenThrowableDoesNotContainConstraintViolation() {
      // 1. Arrange
      var exception = new RuntimeException("unexpected database failure");

      // 2. Act
      var result = extractor.extractUniqueConstraintName(exception);

      // 3. Assert
      assertThat(result).isEmpty();
    }
  }

  private static SQLException uniqueViolationSqlException(String message) {
    return new SQLException(message, "23505");
  }
}
