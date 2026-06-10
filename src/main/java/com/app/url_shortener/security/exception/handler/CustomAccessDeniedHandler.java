package com.app.url_shortener.security.exception.handler;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.error.ProblemDetailFactory;
import com.app.url_shortener.shared.error.ProblemDetailResponseWriter;
import com.app.url_shortener.shared.error.ProblemType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAccessDeniedHandler implements AccessDeniedHandler {

  private final ProblemDetailFactory problemDetailFactory;
  private final ProblemDetailResponseWriter responseWriter;

  @Override
  public void handle(
          HttpServletRequest request,
          HttpServletResponse response,
          AccessDeniedException accessDeniedException
  ) throws IOException {
    ProblemDetail problemDetail = problemDetailFactory.createWithInstance(
            HttpStatus.FORBIDDEN,
            "Acesso negado",
            CommonErrorCode.AUTH_ACCESS_DENIED.getMessage(),
            ProblemType.FORBIDDEN,
            CommonErrorCode.AUTH_ACCESS_DENIED,
            request.getRequestURI()
    );

    responseWriter.write(response, problemDetail);
  }
}
