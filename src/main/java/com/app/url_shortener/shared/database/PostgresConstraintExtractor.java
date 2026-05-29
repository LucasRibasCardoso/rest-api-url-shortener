package com.app.url_shortener.shared.database;

import org.hibernate.exception.ConstraintViolationException;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public class PostgresConstraintExtractor {

  private static final String POSTGRES_UNIQUE_VIOLATION = "23505";
  private static final Pattern UNIQUE_CONSTRAINT_PATTERN = Pattern.compile("unique constraint \"([^\"]+)\"");

  public Optional<String> extractUniqueConstraintName(Throwable throwable) {
    Throwable current = throwable;

    while (current != null) {
      if (current instanceof ConstraintViolationException constraintException
              && isUniqueViolation(constraintException.getSQLException())) {
        return Optional.ofNullable(constraintException.getConstraintName())
                .or(() -> extractFromMessage(constraintException.getSQLException()));
      }

      if (current instanceof SQLException sqlException && isUniqueViolation(sqlException)) {
        return extractFromMessage(sqlException);
      }

      current = current.getCause();
    }

    return Optional.empty();
  }

  private boolean isUniqueViolation(SQLException exception) {
    return exception != null && POSTGRES_UNIQUE_VIOLATION.equals(exception.getSQLState());
  }

  private Optional<String> extractFromMessage(SQLException exception) {
    if (exception == null || exception.getMessage() == null) {
      return Optional.empty();
    }

    var matcher = UNIQUE_CONSTRAINT_PATTERN.matcher(exception.getMessage());
    if (matcher.find()) {
      return Optional.of(matcher.group(1));
    }

    return Optional.empty();
  }
}
