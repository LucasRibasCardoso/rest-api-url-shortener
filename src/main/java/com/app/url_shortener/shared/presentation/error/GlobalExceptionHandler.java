package com.app.url_shortener.shared.presentation.error;

import com.app.url_shortener.shared.exception.AppBusinessException;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.exception.conflict.ConflictException;
import com.app.url_shortener.shared.exception.forbidden.ForbiddenException;
import com.app.url_shortener.shared.exception.internalservererror.InternalServerErrorException;
import com.app.url_shortener.shared.exception.notfound.NotFoundException;
import com.app.url_shortener.shared.exception.ratelimit.TooManyRequestsException;
import com.app.url_shortener.shared.exception.unauthorized.UnauthorizedException;
import com.app.url_shortener.shared.exception.validation.DomainValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.core.exception.SdkException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger LOGGER = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  private final ProblemDetailFactory problemDetailFactory;

  public GlobalExceptionHandler(ProblemDetailFactory problemDetailFactory) {
    this.problemDetailFactory = problemDetailFactory;
  }

  @ExceptionHandler(DomainValidationException.class)
  public ProblemDetail handleDomainValidation(DomainValidationException exception) {
    return buildProblemDetail(
            HttpStatus.BAD_REQUEST,
            "Validação",
            ProblemType.VALIDATION,
            exception
    );
  }

  @ExceptionHandler(ConflictException.class)
  public ProblemDetail handleConflict(ConflictException exception) {
    return buildProblemDetail(
            HttpStatus.CONFLICT,
            "Conflito",
            ProblemType.CONFLICT,
            exception
    );
  }

  @ExceptionHandler(NotFoundException.class)
  public ProblemDetail handleEntityNotFound(NotFoundException exception) {
    return buildProblemDetail(
            HttpStatus.NOT_FOUND,
            "Não encontrado",
            ProblemType.NOT_FOUND,
            exception
    );
  }

  @ExceptionHandler(UnauthorizedException.class)
  public ProblemDetail handleUnauthorized(UnauthorizedException exception) {
    return buildProblemDetail(
            HttpStatus.UNAUTHORIZED,
            "Não autorizado",
            ProblemType.UNAUTHORIZED,
            exception
    );
  }

  @ExceptionHandler(ForbiddenException.class)
  public ProblemDetail handleForbidden(ForbiddenException exception) {
    return buildProblemDetail(
            HttpStatus.FORBIDDEN,
            "Proibido",
            ProblemType.FORBIDDEN,
            exception
    );
  }

  @ExceptionHandler(AuthorizationDeniedException.class)
  public ProblemDetail handleAuthorizationDenied() {
    return problemDetailFactory.create(
            HttpStatus.FORBIDDEN,
            "Acesso negado",
            CommonErrorCode.AUTH_ACCESS_DENIED.getMessage(),
            ProblemType.FORBIDDEN,
            CommonErrorCode.AUTH_ACCESS_DENIED
    );
  }

  @ExceptionHandler(TooManyRequestsException.class)
  public ProblemDetail handleTooManyRequests(TooManyRequestsException exception) {
    return buildProblemDetail(
            HttpStatus.TOO_MANY_REQUESTS,
            "Muitas requisições",
            ProblemType.TOO_MANY_REQUESTS,
            exception
    );
  }

  @ExceptionHandler(InternalServerErrorException.class)
  public ProblemDetail handleInternalServerError(InternalServerErrorException exception) {
    LOGGER.error("Erro interno tratado. errorCode={}", exception.getErrorCode().getCode(), exception);

    return buildProblemDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro interno",
            ProblemType.INFRASTRUCTURE,
            exception,
            exception.getErrorCode().getMessage()
    );
  }

  @ExceptionHandler(AppBusinessException.class)
  public ProblemDetail handleBusinessException(AppBusinessException exception) {
    return buildProblemDetail(
            HttpStatusCode.valueOf(422),
            "Negócio",
            ProblemType.BUSINESS,
            exception
    );
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ProblemDetail handleMethodArgumentNotValid(MethodArgumentNotValidException exception) {
    List<Map<String, String>> errors = exception.getBindingResult()
            .getFieldErrors()
            .stream()
            .map(this::toFieldError)
            .toList();

    return problemDetailFactory.createValidationProblem(
            HttpStatus.BAD_REQUEST,
            "Validação",
            CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage(),
            ProblemType.VALIDATION,
            CommonErrorCode.REQUEST_VALIDATION_FAILED,
            errors
    );
  }

  @ExceptionHandler({SdkException.class, DataAccessException.class})
  public ProblemDetail handleTechnicalDependencyFailure(Exception exception) {
    LOGGER.error("Falha técnica ao comunicar com dependência externa: {}", exception.getMessage());

    return problemDetailFactory.create(
            HttpStatus.SERVICE_UNAVAILABLE,
            "Infraestrutura",
            CommonErrorCode.DEPENDENCY_FAILURE.getMessage(),
            ProblemType.INFRASTRUCTURE,
            CommonErrorCode.DEPENDENCY_FAILURE
    );
  }

  @ExceptionHandler(Exception.class)
  public ProblemDetail handleUnexpected(Exception exception) {
    LOGGER.error("Erro interno inesperado.", exception);

    return problemDetailFactory.create(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro interno inesperado",
            CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage(),
            ProblemType.INFRASTRUCTURE,
            CommonErrorCode.INTERNAL_SERVER_ERROR
    );
  }

  private ProblemDetail buildProblemDetail(
          HttpStatusCode status,
          String title,
          String type,
          AppBusinessException exception
  ) {
    return buildProblemDetail(status, title, type, exception, exception.getMessage());
  }

  private ProblemDetail buildProblemDetail(
          HttpStatusCode status,
          String title,
          String type,
          AppBusinessException exception,
          String detail
  ) {
    return problemDetailFactory.create(
            status,
            title,
            detail,
            type,
            exception.getErrorCode()
    );
  }

  private Map<String, String> toFieldError(FieldError fieldError) {
    return Map.of(
            "field", fieldError.getField(),
            "message", fieldError.getDefaultMessage() == null
                    ? "Invalid value"
                    : fieldError.getDefaultMessage()
    );
  }
}
