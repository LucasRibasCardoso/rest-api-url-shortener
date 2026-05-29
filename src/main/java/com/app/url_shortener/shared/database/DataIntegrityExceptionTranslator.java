package com.app.url_shortener.shared.database;

import com.app.url_shortener.iam.domain.exception.user.EmailAlreadyRegisteredException;
import com.app.url_shortener.shared.exception.conflict.DataIntegrityConflictException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class DataIntegrityExceptionTranslator {

  private final PostgresConstraintExtractor postgresConstraintExtractor;

  public RuntimeException translate(DataIntegrityViolationException exception) {
    return postgresConstraintExtractor.extractUniqueConstraintName(exception)
            .flatMap(DatabaseConstraints::fromValue)
            .map(this::mapConstraintToException)
            .orElseGet(DataIntegrityConflictException::new);
  }

  private RuntimeException mapConstraintToException(DatabaseConstraints constraint) {
    return switch (constraint) {
      case UK_USERS_EMAIL -> new EmailAlreadyRegisteredException();
    };
  }
}
