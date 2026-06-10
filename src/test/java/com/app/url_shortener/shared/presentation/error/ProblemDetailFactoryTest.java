package com.app.url_shortener.shared.presentation.error;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.shared.error.ProblemDetailFactory;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

@Tag("unit")
@DisplayName("Testes de Unidade - ProblemDetailFactory")
class ProblemDetailFactoryTest {

  private final ProblemDetailFactory factory = new ProblemDetailFactory();

  @Nested
  @DisplayName("Criação básica")
  class CreateTests {

    @Test
    @DisplayName("Deve construir ProblemDetail usando errorCode em texto")
    void shouldCreateProblemDetailWithStringErrorCode() {
      // 1. Arrange
      var status = HttpStatus.CONFLICT;
      var title = "Conflito";
      var detail = "Email já cadastrado.";
      var type = ProblemType.CONFLICT;
      var errorCode = "AUTH_EMAIL_ALREADY_EXISTS";

      // 2. Act
      var problemDetail = factory.create(status, title, detail, type, errorCode);

      // 3. Assert
      assertAll(
              () -> assertThat(problemDetail.getStatus()).isEqualTo(status.value()),
              () -> assertThat(problemDetail.getTitle()).isEqualTo(title),
              () -> assertThat(problemDetail.getDetail()).isEqualTo(detail),
              () -> assertThat(problemDetail.getType()).isEqualTo(URI.create(type)),
              () -> assertThat(problemDetail.getProperties()).containsEntry("errorCode", errorCode)
      );
    }

    @Test
    @DisplayName("Deve construir ProblemDetail usando ErrorCode")
    void shouldCreateProblemDetailWithErrorCode() {
      // 1. Arrange
      var status = HttpStatus.UNAUTHORIZED;
      var title = "Não autorizado";
      var detail = CommonErrorCode.AUTH_UNAUTHORIZED.getMessage();
      var type = ProblemType.UNAUTHORIZED;

      // 2. Act
      var problemDetail = factory.create(
              status,
              title,
              detail,
              type,
              CommonErrorCode.AUTH_UNAUTHORIZED
      );

      // 3. Assert
      assertAll(
              () -> assertThat(problemDetail.getStatus()).isEqualTo(status.value()),
              () -> assertThat(problemDetail.getTitle()).isEqualTo(title),
              () -> assertThat(problemDetail.getDetail()).isEqualTo(detail),
              () -> assertThat(problemDetail.getType()).isEqualTo(URI.create(type)),
              () -> assertThat(problemDetail.getProperties())
                      .containsEntry("errorCode", CommonErrorCode.AUTH_UNAUTHORIZED.getCode())
      );
    }

    @Test
    @DisplayName("Deve aceitar HttpStatusCode customizado")
    void shouldCreateProblemDetailWithCustomHttpStatusCode() {
      // 1. Arrange
      var status = HttpStatusCode.valueOf(422);

      // 2. Act
      var problemDetail = factory.create(
              status,
              "Negócio",
              IamErrorCode.AUTH_INVALID_CREDENTIALS.getMessage(),
              ProblemType.BUSINESS,
              IamErrorCode.AUTH_INVALID_CREDENTIALS
      );

      // 3. Assert
      assertAll(
              () -> assertThat(problemDetail.getStatus()).isEqualTo(422),
              () -> assertThat(problemDetail.getTitle()).isEqualTo("Negócio"),
              () -> assertThat(problemDetail.getDetail())
                      .isEqualTo(IamErrorCode.AUTH_INVALID_CREDENTIALS.getMessage()),
              () -> assertThat(problemDetail.getType()).isEqualTo(URI.create(ProblemType.BUSINESS)),
              () -> assertThat(problemDetail.getProperties())
                      .containsEntry("errorCode", IamErrorCode.AUTH_INVALID_CREDENTIALS.getCode())
      );
    }
  }

  @Nested
  @DisplayName("Criação com instance")
  class CreateWithInstanceTests {

    @Test
    @DisplayName("Deve configurar instance quando valor for informado")
    void shouldSetInstanceWhenValueIsProvided() {
      // 1. Arrange
      var instance = "/api/v1/urls";

      // 2. Act
      var problemDetail = factory.createWithInstance(
              HttpStatus.FORBIDDEN,
              "Proibido",
              CommonErrorCode.AUTH_ACCESS_DENIED.getMessage(),
              ProblemType.FORBIDDEN,
              CommonErrorCode.AUTH_ACCESS_DENIED,
              instance
      );

      // 3. Assert
      assertAll(
              () -> assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value()),
              () -> assertThat(problemDetail.getTitle()).isEqualTo("Proibido"),
              () -> assertThat(problemDetail.getDetail()).isEqualTo(CommonErrorCode.AUTH_ACCESS_DENIED.getMessage()),
              () -> assertThat(problemDetail.getType()).isEqualTo(URI.create(ProblemType.FORBIDDEN)),
              () -> assertThat(problemDetail.getInstance()).isEqualTo(URI.create(instance)),
              () -> assertThat(problemDetail.getProperties())
                      .containsEntry("errorCode", CommonErrorCode.AUTH_ACCESS_DENIED.getCode())
      );
    }

    @Test
    @DisplayName("Não deve configurar instance quando valor for nulo")
    void shouldNotSetInstanceWhenValueIsNull() {
      // 1. Arrange
      String instance = null;

      // 2. Act
      var problemDetail = factory.createWithInstance(
              HttpStatus.BAD_REQUEST,
              "Validação",
              CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
              ProblemType.VALIDATION,
              CommonErrorCode.REQUEST_VALIDATION_FAILED,
              instance
      );

      // 3. Assert
      assertThat(problemDetail.getInstance()).isNull();
    }

    @Test
    @DisplayName("Não deve configurar instance quando valor estiver em branco")
    void shouldNotSetInstanceWhenValueIsBlank() {
      // 1. Arrange
      var instance = "   ";

      // 2. Act
      var problemDetail = factory.createWithInstance(
              HttpStatus.BAD_REQUEST,
              "Validação",
              CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
              ProblemType.VALIDATION,
              CommonErrorCode.REQUEST_VALIDATION_FAILED,
              instance
      );

      // 3. Assert
      assertThat(problemDetail.getInstance()).isNull();
    }
  }

  @Nested
  @DisplayName("Problema de validação")
  class ValidationProblemTests {

    @Test
    @DisplayName("Deve construir ProblemDetail de validação com lista de erros")
    void shouldCreateValidationProblemDetailWithErrors() {
      // 1. Arrange
      var errors = List.of(
              Map.of("field", "email", "message", "must be a well-formed email address"),
              Map.of("field", "password", "message", "must have at least 8 characters")
      );

      // 2. Act
      var problemDetail = factory.createValidationProblem(
              HttpStatus.BAD_REQUEST,
              "Validação",
              CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
              ProblemType.VALIDATION,
              CommonErrorCode.REQUEST_VALIDATION_FAILED,
              errors
      );

      // 3. Assert
      assertAll(
              () -> assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
              () -> assertThat(problemDetail.getTitle()).isEqualTo("Validação"),
              () -> assertThat(problemDetail.getDetail())
                      .isEqualTo(CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage()),
              () -> assertThat(problemDetail.getType()).isEqualTo(URI.create(ProblemType.VALIDATION)),
              () -> assertThat(problemDetail.getProperties())
                      .containsEntry("errorCode", CommonErrorCode.REQUEST_VALIDATION_FAILED.getCode()),
              () -> assertThat(problemDetail.getProperties()).containsEntry("errors", errors)
      );
    }

    @Test
    @DisplayName("Deve preservar lista de erros vazia")
    void shouldPreserveEmptyErrorsList() {
      // 1. Arrange
      List<Map<String, String>> errors = List.of();

      // 2. Act
      var problemDetail = factory.createValidationProblem(
              HttpStatus.BAD_REQUEST,
              "Validação",
              CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
              ProblemType.VALIDATION,
              CommonErrorCode.REQUEST_VALIDATION_FAILED,
              errors
      );

      // 3. Assert
      assertThat(problemDetail.getProperties()).containsEntry("errors", errors);
    }
  }
}
