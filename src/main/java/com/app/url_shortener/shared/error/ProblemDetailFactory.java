package com.app.url_shortener.shared.error;

import com.app.url_shortener.shared.exception.ErrorCode;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

@Component
public class ProblemDetailFactory {

  public ProblemDetail create(
      HttpStatusCode status, String title, String detail, String type, String errorCode) {
    ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
    problemDetail.setTitle(title);
    problemDetail.setType(URI.create(type));
    problemDetail.setProperty("errorCode", errorCode);

    return problemDetail;
  }

  public ProblemDetail create(
      HttpStatusCode status, String title, String detail, String type, ErrorCode errorCode) {
    return create(status, title, detail, type, errorCode.getCode());
  }

  public ProblemDetail createWithInstance(
      HttpStatusCode status,
      String title,
      String detail,
      String type,
      String errorCode,
      String instance) {
    ProblemDetail problemDetail = create(status, title, detail, type, errorCode);

    if (instance != null && !instance.isBlank()) {
      problemDetail.setInstance(URI.create(instance));
    }

    return problemDetail;
  }

  public ProblemDetail createWithInstance(
      HttpStatusCode status,
      String title,
      String detail,
      String type,
      ErrorCode errorCode,
      String instance) {
    return createWithInstance(status, title, detail, type, errorCode.getCode(), instance);
  }

  public ProblemDetail createValidationProblem(
      HttpStatusCode status,
      String title,
      String detail,
      String type,
      ErrorCode errorCode,
      List<Map<String, String>> errors) {
    ProblemDetail problemDetail = create(status, title, detail, type, errorCode);
    problemDetail.setProperty("errors", errors);

    return problemDetail;
  }
}
