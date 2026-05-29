package com.app.url_shortener.shared.database.persistence;

import com.app.url_shortener.iam.domain.exception.auth.AuthErrorCode;
import com.app.url_shortener.iam.domain.exception.user.EmailAlreadyRegisteredException;
import com.app.url_shortener.shared.database.DataIntegrityExceptionTranslator;
import com.app.url_shortener.shared.database.DatabaseConstraints;
import com.app.url_shortener.shared.database.PostgresConstraintExtractor;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.conflict.DataIntegrityConflictException;
import com.app.url_shortener.shared.exception.conflict.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - DataIntegrityExceptionTranslator")
class DataIntegrityExceptionTranslatorTest {

  @Mock
  private PostgresConstraintExtractor postgresConstraintExtractor;

  @InjectMocks
  private DataIntegrityExceptionTranslator translator;

  @Nested
  @DisplayName("Tradução de integridade")
  class TranslateTests {

    @Test
    @DisplayName("Deve traduzir constraint de email único para exceção de domínio")
    void shouldTranslateUniqueEmailConstraintToDomainException() {
      // 1. Arrange
      var exception = dataIntegrityViolationException();

      given(postgresConstraintExtractor.extractUniqueConstraintName(exception))
              .willReturn(Optional.of(DatabaseConstraints.UK_USERS_EMAIL.value()));

      // 2. Act
      var result = translator.translate(exception);

      // 3. Assert
      assertThat(result)
              .isInstanceOf(EmailAlreadyRegisteredException.class)
              .hasMessage("Email já cadastrado.");
      assertThat(((EmailAlreadyRegisteredException) result).getErrorCode())
              .isEqualTo(AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS);

      verify(postgresConstraintExtractor).extractUniqueConstraintName(exception);
      verifyNoMoreInteractions(postgresConstraintExtractor);
    }

    @Test
    @DisplayName("Deve traduzir constraint desconhecida para conflito genérico")
    void shouldTranslateUnknownConstraintToGenericConflictException() {
      // 1. Arrange
      var exception = dataIntegrityViolationException();

      given(postgresConstraintExtractor.extractUniqueConstraintName(exception))
              .willReturn(Optional.of("uk_unknown_constraint"));

      // 2. Act
      var result = translator.translate(exception);

      // 3. Assert
      assertThat(result)
              .isInstanceOf(DataIntegrityConflictException.class)
              .hasMessage("Violação de integridade dos dados.");
      assertThat(((ConflictException) result).getErrorCode())
              .isEqualTo(CommonErrorCode.DATA_INTEGRITY_CONFLICT);

      verify(postgresConstraintExtractor).extractUniqueConstraintName(exception);
      verifyNoMoreInteractions(postgresConstraintExtractor);
    }

    @Test
    @DisplayName("Deve traduzir ausência de constraint para conflito genérico")
    void shouldTranslateMissingConstraintToGenericConflictException() {
      // 1. Arrange
      var exception = dataIntegrityViolationException();

      given(postgresConstraintExtractor.extractUniqueConstraintName(exception))
              .willReturn(Optional.empty());

      // 2. Act
      var result = translator.translate(exception);

      // 3. Assert
      assertThat(result)
              .isInstanceOf(DataIntegrityConflictException.class)
              .hasMessage("Violação de integridade dos dados.");
      assertThat(((ConflictException) result).getErrorCode())
              .isEqualTo(CommonErrorCode.DATA_INTEGRITY_CONFLICT);

      verify(postgresConstraintExtractor).extractUniqueConstraintName(exception);
      verifyNoMoreInteractions(postgresConstraintExtractor);
    }
  }

  private static DataIntegrityViolationException dataIntegrityViolationException() {
    return new DataIntegrityViolationException("database constraint violation");
  }
}
