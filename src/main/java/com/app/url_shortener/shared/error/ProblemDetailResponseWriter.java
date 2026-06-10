package com.app.url_shortener.shared.error;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ProblemDetailResponseWriter {

  private final ObjectMapper objectMapper;

  public void write(HttpServletResponse response, ProblemDetail problemDetail) throws IOException {
    response.setStatus(problemDetail.getStatus());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding(StandardCharsets.UTF_8.name());

    objectMapper.writeValue(response.getOutputStream(), problemDetail);
  }
}