package com.app.url_shortener.shared.error;

import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;
import com.app.url_shortener.shared.exception.forbidden.ForbiddenException;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;
import com.app.url_shortener.shared.exception.notfound.NotFoundException;
import com.app.url_shortener.shared.exception.unauthorized.UnauthorizedException;
import com.app.url_shortener.shared.exception.validation.DomainValidationException;
import com.app.url_shortener.shared.ratelimit.exception.TooManyRequestsException;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import software.amazon.awssdk.core.exception.SdkException;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

  private final ProblemDetailFactory problemDetailFactory;

  public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
    this.problemDetailFactory = problemDetailFactory;
  }

  @ExceptionHandler(DomainValidationException.class)
  public ProblemDetail handleDomainValidation(DomainValidationException exception) {
    return buildProblemDetail(
        HttpStatus.BAD_REQUEST, "Validação", ProblemType.VALIDATION, exception);
  }

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException exception) {
    return buildProblemDetail(HttpStatus.CONFLICT, "Conflito", ProblemType.CONFLICT, exception);
  }

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleEntityNotFound(NotFoundException exception) {
    return buildProblemDetail(
        HttpStatus.NOT_FOUND, "Não encontrado", ProblemType.NOT_FOUND, exception);
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ProblemDetail handleUnauthorized(UnauthorizedException exception) {
    return buildProblemDetail(
        HttpStatus.UNAUTHORIZED, "Não autorizado", ProblemType.UNAUTHORIZED, exception);
  }

  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail handleForbidden(ForbiddenException exception) {
    return buildProblemDetail(HttpStatus.FORBIDDEN, "Proibido", ProblemType.FORBIDDEN, exception);
  }

  @ExceptionHandler(AuthorizationDeniedException.class)
  public ProblemDetail handleAuthorizationDenied() {
    return problemDetailFactory.create(
        HttpStatus.FORBIDDEN,
        "Acesso negado",
        CommonErrorCode.AUTH_ACCESS_DENIED.getMessage(),
        ProblemType.FORBIDDEN,
        CommonErrorCode.AUTH_ACCESS_DENIED);
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ResponseEntity<ProblemDetail> handleTooManyRequests(TooManyRequestsException exception) {
    ProblemDetail problemDetail =
        buildProblemDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Muitas requisições",
            ProblemType.TOO_MANY_REQUESTS,
            exception);

    HttpHeaders headers = new HttpHeaders();
    headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterInSeconds()));

    return new ResponseEntity<>(problemDetail, headers, HttpStatus.TOO_MANY_REQUESTS);
  }

  @ExceptionHandler(InternalServerErrorException.class)
  public ProblemDetail handleInternalServerError(InternalServerErrorException exception) {
    log.error("Erro interno tratado. errorCode={}", exception.getErrorCode().getCode(), exception);

    return buildProblemDetail(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Erro interno",
        ProblemType.INFRASTRUCTURE,
        exception,
        exception.getErrorCode().getMessage());
  }

  @ExceptionHandler(AppBusinessException.class)
  public ProblemDetail handleBusinessException(AppBusinessException exception) {
    return buildProblemDetail(
        HttpStatusCode.valueOf(422), "Negócio", ProblemType.BUSINESS, exception);
  }

  @Override
  protected ResponseEntity<Object> handleMethodArgumentNotValid(
      MethodArgumentNotValidException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return new ResponseEntity<>(handleMethodArgumentNotValid(exception), headers, status);
  }

  public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
    List<Map<String, String>> errors =
        exception.getBindingResult().getFieldErrors().stream().map(this::toFieldError).toList();

    return problemDetailFactory.createValidationProblem(
        HttpStatus.BAD_REQUEST,
        "Validação",
        CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
        ProblemType.VALIDATION,
        CommonErrorCode.REQUEST_VALIDATION_FAILED,
        errors);
  }

  @Override
  protected ResponseEntity<Object> handleHandlerMethodValidationException(
      HandlerMethodValidationException exception,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return new ResponseEntity<>(handleHandlerMethodValidation(exception), headers, status);
  }

  public ProblemDetail handleHandlerMethodValidation(HandlerMethodValidationException exception) {
    List<Map<String, String>> errors =
        exception.getParameterValidationResults().stream()
            .flatMap(parameterResult -> parameterResult.getResolvableErrors().stream()
                .map(error -> toParameterError(parameterResult, error)))
            .toList();

    return problemDetailFactory.createValidationProblem(
        HttpStatus.BAD_REQUEST,
        "Validação",
        CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
        ProblemType.VALIDATION,
        CommonErrorCode.REQUEST_VALIDATION_FAILED,
        errors);
  }

  @ExceptionHandler({SdkException.class, DataAccessException.class})
  public ProblemDetail handleTechnicalDependencyFailure(Exception exception) {
    log.error("Falha técnica ao comunicar com dependência externa: {}", exception.getMessage());

    return problemDetailFactory.create(
        HttpStatus.SERVICE_UNAVAILABLE,
        "Infraestrutura",
        CommonErrorCode.DEPENDENCY_FAILURE.getMessage(),
        ProblemType.INFRASTRUCTURE,
        CommonErrorCode.DEPENDENCY_FAILURE);
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception) {
    log.error("Erro interno inesperado.", exception);

    return problemDetailFactory.create(
        HttpStatus.INTERNAL_SERVER_ERROR,
        "Erro interno inesperado",
        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
        ProblemType.INFRASTRUCTURE,
        CommonErrorCode.INTERNAL_SERVER_ERROR);
  }

  private ProblemDetail buildProblemDetail(
      HttpStatusCode status, String title, String type, AppBusinessException exception) {
    return buildProblemDetail(status, title, type, exception, exception.getMessage());
  }

  private ProblemDetail buildProblemDetail(
      HttpStatusCode status,
      String title,
      String type,
      AppBusinessException exception,
      String detail) {
    return problemDetailFactory.create(status, title, detail, type, exception.getErrorCode());
  }

  private Map<String, String> toFieldError(FieldError fieldError) {
    return Map.of(
        "field",
        fieldError.getField(),
        "message",
        fieldError.getDefaultMessage() == null ? "Invalid value" : fieldError.getDefaultMessage());
  }

  private Map<String, String> toParameterError(
      ParameterValidationResult parameterResult, MessageSourceResolvable error) {
    String parameterName = parameterResult.getMethodParameter().getParameterName();

    return Map.of(
        "field",
        parameterName == null ? "arg" + parameterResult.getMethodParameter().getParameterIndex() : parameterName,
        "message",
        error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage());
  }
}
