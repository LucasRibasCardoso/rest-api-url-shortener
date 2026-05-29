package com.app.url_shortener.shared.presentation.error;

import com.app.url_shortener.iam.domain.exception.auth.AccountLockedException;
import com.app.url_shortener.iam.domain.exception.auth.AuthErrorCode;
import com.app.url_shortener.iam.domain.exception.auth.InvalidCredentialsException;
import com.app.url_shortener.iam.domain.exception.auth.InvalidRefreshTokenException;
import com.app.url_shortener.iam.domain.exception.user.EmailAlreadyRegisteredException;
import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.ErrorCode;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;
import com.app.url_shortener.shared.ratelimit.exception.TooManyRequestsException;
import com.app.url_shortener.url.domain.exception.UrlErrorCode;
import com.app.url_shortener.url.domain.exception.UrlNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.MethodParameter;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

  @Mock
  private ProblemDetailFactory problemDetailFactory;

  @InjectMocks
  private GlobalExceptionHandler handler;

  @Nested
  @DisplayName("Mapeamento de exceções")
  class ExceptionMappingTests {

    @Test
    @DisplayName("Deve mapear DomainValidationException para 400 e ProblemType de validação")
    void shouldMapDomainValidationExceptionToBadRequest() {
      // 1. Arrange
      var exception = new InvalidCredentialsException();
      var problemDetail = problemDetail(HttpStatus.BAD_REQUEST);

      given(problemDetailFactory.create(
              HttpStatus.BAD_REQUEST,
              "Validação",
              exception.getMessage(),
              ProblemType.VALIDATION,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleDomainValidation(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              HttpStatus.BAD_REQUEST,
              "Validação",
              AuthErrorCode.AUTH_INVALID_CREDENTIALS.getMessage(),
              ProblemType.VALIDATION,
              AuthErrorCode.AUTH_INVALID_CREDENTIALS
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear ConflictException para 409 e ProblemType de conflito")
    void shouldMapConflictExceptionToConflict() {
      // 1. Arrange
      var exception = new EmailAlreadyRegisteredException();
      var problemDetail = problemDetail(HttpStatus.CONFLICT);

      given(problemDetailFactory.create(
              HttpStatus.CONFLICT,
              "Conflito",
              exception.getMessage(),
              ProblemType.CONFLICT,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleConflict(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              HttpStatus.CONFLICT,
              "Conflito",
              AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS.getMessage(),
              ProblemType.CONFLICT,
              AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear NotFoundException para 404 e ProblemType de não encontrado")
    void shouldMapNotFoundExceptionToNotFound() {
      // 1. Arrange
      var exception = new UrlNotFoundException();
      var problemDetail = problemDetail(HttpStatus.NOT_FOUND);

      given(problemDetailFactory.create(
              HttpStatus.NOT_FOUND,
              "Não encontrado",
              exception.getMessage(),
              ProblemType.NOT_FOUND,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleEntityNotFound(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              HttpStatus.NOT_FOUND,
              "Não encontrado",
              UrlErrorCode.URL_NOT_FOUND.getMessage(),
              ProblemType.NOT_FOUND,
              UrlErrorCode.URL_NOT_FOUND
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear UnauthorizedException para 401 e ProblemType de não autorizado")
    void shouldMapUnauthorizedExceptionToUnauthorized() {
      // 1. Arrange
      var exception = new InvalidRefreshTokenException();
      var problemDetail = problemDetail(HttpStatus.UNAUTHORIZED);

      given(problemDetailFactory.create(
              HttpStatus.UNAUTHORIZED,
              "Não autorizado",
              exception.getMessage(),
              ProblemType.UNAUTHORIZED,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleUnauthorized(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              HttpStatus.UNAUTHORIZED,
              "Não autorizado",
              AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID.getMessage(),
              ProblemType.UNAUTHORIZED,
              AuthErrorCode.AUTH_REFRESH_TOKEN_INVALID
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear ForbiddenException para 403 e ProblemType de proibido")
    void shouldMapForbiddenExceptionToForbidden() {
      // 1. Arrange
      var exception = new AccountLockedException();
      var problemDetail = problemDetail(HttpStatus.FORBIDDEN);

      given(problemDetailFactory.create(
              HttpStatus.FORBIDDEN,
              "Proibido",
              exception.getMessage(),
              ProblemType.FORBIDDEN,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleForbidden(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              HttpStatus.FORBIDDEN,
              "Proibido",
              AuthErrorCode.AUTH_ACCOUNT_LOCKED.getMessage(),
              ProblemType.FORBIDDEN,
              AuthErrorCode.AUTH_ACCOUNT_LOCKED
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear TooManyRequestsException para 429 com Retry-After em segundos")
    void shouldMapTooManyRequestsExceptionToTooManyRequests() {
      // 1. Arrange
      var exception = new TestTooManyRequestsException(Duration.ofSeconds(30));
      var problemDetail = problemDetail(HttpStatus.TOO_MANY_REQUESTS);

      given(problemDetailFactory.create(
              HttpStatus.TOO_MANY_REQUESTS,
              "Muitas requisições",
              exception.getMessage(),
              ProblemType.TOO_MANY_REQUESTS,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleTooManyRequests(exception);

      // 3. Assert
      assertAll(
              () -> assertThat(result.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS),
              () -> assertThat(result.getBody()).isSameAs(problemDetail),
              () -> assertThat(result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("30")
      );

      verify(problemDetailFactory).create(
              HttpStatus.TOO_MANY_REQUESTS,
              "Muitas requisições",
              TestErrorCode.RATE_LIMITED.getMessage(),
              ProblemType.TOO_MANY_REQUESTS,
              TestErrorCode.RATE_LIMITED
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve arredondar Retry-After para cima quando houver fração de segundo")
    void shouldRoundRetryAfterUpWhenDurationHasFractionalSeconds() {
      // 1. Arrange
      var exception = new TestTooManyRequestsException(Duration.ofMillis(1500));
      var problemDetail = problemDetail(HttpStatus.TOO_MANY_REQUESTS);

      given(problemDetailFactory.create(
              HttpStatus.TOO_MANY_REQUESTS,
              "Muitas requisições",
              exception.getMessage(),
              ProblemType.TOO_MANY_REQUESTS,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleTooManyRequests(exception);

      // 3. Assert
      assertThat(result.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("2");

      verify(problemDetailFactory).create(
              HttpStatus.TOO_MANY_REQUESTS,
              "Muitas requisições",
              TestErrorCode.RATE_LIMITED.getMessage(),
              ProblemType.TOO_MANY_REQUESTS,
              TestErrorCode.RATE_LIMITED
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear AppBusinessException genérica para 422 e ProblemType de negócio")
    void shouldMapGenericBusinessExceptionToUnprocessableEntity() {
      // 1. Arrange
      var exception = new TestBusinessException();
      var status = HttpStatusCode.valueOf(422);
      var problemDetail = problemDetail(status);

      given(problemDetailFactory.create(
              status,
              "Negócio",
              exception.getMessage(),
              ProblemType.BUSINESS,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleBusinessException(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              status,
              "Negócio",
              TestErrorCode.BUSINESS_RULE.getMessage(),
              ProblemType.BUSINESS,
              TestErrorCode.BUSINESS_RULE
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear InternalServerErrorException para 500 e ProblemType de infraestrutura")
    void shouldMapInternalServerErrorExceptionToInternalServerError() {
      // 1. Arrange
      var exception = new TestInternalServerErrorException();
      var problemDetail = problemDetail(HttpStatus.INTERNAL_SERVER_ERROR);

      given(problemDetailFactory.create(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "Erro interno",
              exception.getErrorCode().getMessage(),
              ProblemType.INFRASTRUCTURE,
              exception.getErrorCode()
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleInternalServerError(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "Erro interno",
              TestErrorCode.INTERNAL_FAILURE.getMessage(),
              ProblemType.INFRASTRUCTURE,
              TestErrorCode.INTERNAL_FAILURE
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear MethodArgumentNotValidException para 400 com erros de campos")
    void shouldMapMethodArgumentNotValidExceptionToValidationProblem() throws Exception {
      // 1. Arrange
      var exception = methodArgumentNotValidException();
      var problemDetail = problemDetail(HttpStatus.BAD_REQUEST);
      var expectedErrors = List.of(
              Map.of("field", "email", "message", "must be a well-formed email address"),
              Map.of("field", "name", "message", "Invalid value")
      );

      given(problemDetailFactory.createValidationProblem(
              HttpStatus.BAD_REQUEST,
              "Validação",
              CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
              ProblemType.VALIDATION,
              CommonErrorCode.REQUEST_VALIDATION_FAILED,
              expectedErrors
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleMethodArgumentNotValid(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).createValidationProblem(
              HttpStatus.BAD_REQUEST,
              "Validação",
              CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
              ProblemType.VALIDATION,
              CommonErrorCode.REQUEST_VALIDATION_FAILED,
              expectedErrors
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear falha técnica de dependência para 503 e ProblemType de infraestrutura")
    void shouldMapTechnicalDependencyFailureToServiceUnavailable() {
      // 1. Arrange
      var exception = new DataAccessResourceFailureException("database unavailable");
      var problemDetail = problemDetail(HttpStatus.SERVICE_UNAVAILABLE);

      given(problemDetailFactory.create(
              HttpStatus.SERVICE_UNAVAILABLE,
              "Infraestrutura",
              CommonErrorCode.DEPENDENCY_FAILURE.getMessage(),
              ProblemType.INFRASTRUCTURE,
              CommonErrorCode.DEPENDENCY_FAILURE
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleTechnicalDependencyFailure(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              HttpStatus.SERVICE_UNAVAILABLE,
              "Infraestrutura",
              CommonErrorCode.DEPENDENCY_FAILURE.getMessage(),
              ProblemType.INFRASTRUCTURE,
              CommonErrorCode.DEPENDENCY_FAILURE
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }

    @Test
    @DisplayName("Deve mapear exceção inesperada para 500 e ProblemType de infraestrutura")
    void shouldMapUnexpectedExceptionToInternalServerError() {
      // 1. Arrange
      var exception = new RuntimeException("unexpected");
      var problemDetail = problemDetail(HttpStatus.INTERNAL_SERVER_ERROR);

      given(problemDetailFactory.create(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "Erro interno inesperado",
              CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
              ProblemType.INFRASTRUCTURE,
              CommonErrorCode.INTERNAL_SERVER_ERROR
      )).willReturn(problemDetail);

      // 2. Act
      var result = handler.handleUnexpected(exception);

      // 3. Assert
      assertThat(result).isSameAs(problemDetail);

      verify(problemDetailFactory).create(
              HttpStatus.INTERNAL_SERVER_ERROR,
              "Erro interno inesperado",
              CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
              ProblemType.INFRASTRUCTURE,
              CommonErrorCode.INTERNAL_SERVER_ERROR
      );
      verifyNoMoreInteractions(problemDetailFactory);
    }
  }

  @Nested
  @DisplayName("ProblemDetailFactory")
  class ProblemDetailFactoryTests {

    private final ProblemDetailFactory factory = new ProblemDetailFactory();

    @Test
    @DisplayName("Deve construir ProblemDetail com status, título, tipo e errorCode")
    void shouldCreateProblemDetailWithStatusTitleTypeAndErrorCode() {
      // 1. Arrange
      var status = HttpStatus.CONFLICT;

      // 2. Act
      var problemDetail = factory.create(
              status,
              "Conflito",
              "Email já cadastrado.",
              ProblemType.CONFLICT,
              AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS
      );

      // 3. Assert
      assertAll(
              () -> assertThat(problemDetail.getStatus()).isEqualTo(status.value()),
              () -> assertThat(problemDetail.getTitle()).isEqualTo("Conflito"),
              () -> assertThat(problemDetail.getDetail()).isEqualTo("Email já cadastrado."),
              () -> assertThat(problemDetail.getType()).isEqualTo(URI.create(ProblemType.CONFLICT)),
              () -> assertThat(problemDetail.getProperties()).containsEntry(
                      "errorCode",
                      AuthErrorCode.AUTH_EMAIL_ALREADY_EXISTS.getCode()
              )
      );
    }

    @Test
    @DisplayName("Deve construir ProblemDetail com instance quando URI for informada")
    void shouldCreateProblemDetailWithInstanceWhenInstanceIsProvided() {
      // 1. Arrange
      var instance = "/api/v1/urls";

      // 2. Act
      var problemDetail = factory.createWithInstance(
              HttpStatus.UNAUTHORIZED,
              "Não autorizado",
              CommonErrorCode.AUTH_UNAUTHORIZED.getMessage(),
              ProblemType.UNAUTHORIZED,
              CommonErrorCode.AUTH_UNAUTHORIZED,
              instance
      );

      // 3. Assert
      assertAll(
              () -> assertThat(problemDetail.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value()),
              () -> assertThat(problemDetail.getTitle()).isEqualTo("Não autorizado"),
              () -> assertThat(problemDetail.getDetail()).isEqualTo(CommonErrorCode.AUTH_UNAUTHORIZED.getMessage()),
              () -> assertThat(problemDetail.getType()).isEqualTo(URI.create(ProblemType.UNAUTHORIZED)),
              () -> assertThat(problemDetail.getInstance()).isEqualTo(URI.create(instance)),
              () -> assertThat(problemDetail.getProperties()).containsEntry(
                      "errorCode",
                      CommonErrorCode.AUTH_UNAUTHORIZED.getCode()
              )
      );
    }

    @Test
    @DisplayName("Deve construir ProblemDetail de validação com lista de erros")
    void shouldCreateValidationProblemDetailWithErrors() {
      // 1. Arrange
      var errors = List.of(Map.of("field", "email", "message", "must be valid"));

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
              () -> assertThat(problemDetail.getType()).isEqualTo(URI.create(ProblemType.VALIDATION)),
              () -> assertThat(problemDetail.getProperties()).containsEntry(
                      "errorCode",
                      CommonErrorCode.REQUEST_VALIDATION_FAILED.getCode()
              ),
              () -> assertThat(problemDetail.getProperties()).containsEntry("errors", errors)
      );
    }
  }

  @Nested
  @DisplayName("ProblemDetailResponseWriter")
  class ProblemDetailResponseWriterTests {

    @Test
    @DisplayName("Deve configurar resposta e serializar ProblemDetail como application/problem+json")
    void shouldConfigureResponseAndSerializeProblemDetailAsProblemJson() throws Exception {
      // 1. Arrange
      var response = new MockHttpServletResponse();
      var problemDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Campo inválido.");
      problemDetail.setTitle("Validação");
      problemDetail.setType(URI.create(ProblemType.VALIDATION));
      problemDetail.setProperty("errorCode", CommonErrorCode.REQUEST_VALIDATION_FAILED.getCode());
      var writer = new ProblemDetailResponseWriter(new ObjectMapper());

      // 2. Act
      writer.write(response, problemDetail);

      // 3. Assert
      var body = response.getContentAsString(StandardCharsets.UTF_8);

      assertAll(
              () -> assertThat(response.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value()),
              () -> assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE),
              () -> assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name()),
              () -> assertThat(body).contains("\"status\":400"),
              () -> assertThat(body).contains("\"title\":\"Validação\""),
              () -> assertThat(body).contains("\"detail\":\"Campo inválido.\""),
              () -> assertThat(body).contains("\"type\":\"/errors/validation\""),
              () -> assertThat(body).contains("\"errorCode\":\"REQUEST_VALIDATION_FAILED\"")
      );
    }
  }

  private static ProblemDetail problemDetail(HttpStatusCode status) {
    return ProblemDetail.forStatus(status);
  }

  private static MethodArgumentNotValidException methodArgumentNotValidException() throws Exception {
    var target = new ValidationTarget();
    var bindingResult = new BeanPropertyBindingResult(target, "validationTarget");
    bindingResult.addError(new FieldError(
            "validationTarget",
            "email",
            "must be a well-formed email address"
    ));
    bindingResult.addError(new FieldError("validationTarget", "name", null));

    return new MethodArgumentNotValidException(methodParameter(), bindingResult);
  }

  private static MethodParameter methodParameter() throws NoSuchMethodException {
    Method method = ValidationController.class.getDeclaredMethod("create", ValidationTarget.class);
    return new MethodParameter(method, 0);
  }

  private enum TestErrorCode implements ErrorCode {
    RATE_LIMITED("Limite de requisições excedido."),
    BUSINESS_RULE("Regra de negócio violada."),
    INTERNAL_FAILURE("Falha interna.");

    private final String message;

    TestErrorCode(String message) {
      this.message = message;
    }

    @Override
    public String getCode() {
      return name();
    }

    @Override
    public String getMessage() {
      return message;
    }
  }

  private static class TestTooManyRequestsException extends TooManyRequestsException {

    TestTooManyRequestsException(Duration retryAfter) {
      super(TestErrorCode.RATE_LIMITED, retryAfter);
    }
  }

  private static class TestBusinessException extends AppBusinessException {

    TestBusinessException() {
      super(TestErrorCode.BUSINESS_RULE);
    }
  }

  private static class TestInternalServerErrorException extends InternalServerErrorException {

    TestInternalServerErrorException() {
      super(TestErrorCode.INTERNAL_FAILURE);
    }
  }

  private record ValidationTarget(String email, String name) {

    ValidationTarget() {
      this(null, null);
    }
  }

  private static class ValidationController {

    @SuppressWarnings("unused")
    void create(ValidationTarget target) {
    }
  }
}
