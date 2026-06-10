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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private final ProblemDetailFactory problemDetailFactory;
  private final ProblemDetailResponseWriter responseWriter;

  @Override
  public void commence(
          HttpServletRequest request,
          HttpServletResponse response,
          AuthenticationException authException
  ) throws IOException {
    ProblemDetail problemDetail = problemDetailFactory.createWithInstance(
            HttpStatus.UNAUTHORIZED,
            "Não autorizado",
            CommonErrorCode.AUTH_UNAUTHORIZED.getMessage(),
            ProblemType.UNAUTHORIZED,
            CommonErrorCode.AUTH_UNAUTHORIZED,
            request.getRequestURI()
    );

    responseWriter.write(response, problemDetail);
  }
}
