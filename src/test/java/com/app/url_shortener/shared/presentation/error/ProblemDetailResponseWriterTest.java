package com.app.url_shortener.shared.presentation.error;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.app.url_shortener.shared.error.ProblemDetailResponseWriter;
import com.app.url_shortener.shared.error.ProblemType;
import com.app.url_shortener.shared.exception.CommonErrorCode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

@Tag("unit")
@DisplayName("Testes de Unidade - ProblemDetailResponseWriter")
class ProblemDetailResponseWriterTest {

  private final ProblemDetailResponseWriter writer =
      new ProblemDetailResponseWriter(new ObjectMapper());

  @Nested
  @DisplayName("Escrita da resposta")
  class WriteTests {

    @Test
    @DisplayName("Deve configurar status, content type e encoding da resposta")
    void shouldConfigureResponseStatusContentTypeAndEncoding() throws Exception {
      // 1. Arrange
      var response = new MockHttpServletResponse();
      var problemDetail =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.UNAUTHORIZED, CommonErrorCode.AUTH_UNAUTHORIZED.getMessage());
      problemDetail.setTitle("Não autorizado");
      problemDetail.setType(URI.create(ProblemType.UNAUTHORIZED));
      problemDetail.setProperty("errorCode", CommonErrorCode.AUTH_UNAUTHORIZED.getCode());

      // 2. Act
      writer.write(response, problemDetail);

      // 3. Assert
      assertAll(
          () -> assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value()),
          () ->
              assertThat(response.getContentType())
                  .startsWith(MediaType.APPLICATION_PROBLEM_JSON_VALUE),
          () ->
              assertThat(response.getCharacterEncoding()).isEqualTo(StandardCharsets.UTF_8.name()),
          () -> assertThat(response.getContentAsString(StandardCharsets.UTF_8)).isNotBlank());
    }

    @Test
    @DisplayName("Deve serializar campos padrão do ProblemDetail")
    void shouldSerializeStandardProblemDetailFields() throws Exception {
      // 1. Arrange
      var response = new MockHttpServletResponse();
      var problemDetail =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.FORBIDDEN, CommonErrorCode.AUTH_ACCESS_DENIED.getMessage());
      problemDetail.setTitle("Proibido");
      problemDetail.setType(URI.create(ProblemType.FORBIDDEN));
      problemDetail.setInstance(URI.create("/api/v1/urls"));

      // 2. Act
      writer.write(response, problemDetail);

      // 3. Assert
      var body = response.getContentAsString(StandardCharsets.UTF_8);

      assertAll(
          () -> assertThat(body).contains("\"type\":\"/errors/forbidden\""),
          () -> assertThat(body).contains("\"title\":\"Proibido\""),
          () -> assertThat(body).contains("\"status\":403"),
          () -> assertThat(body).contains("\"detail\":\"Acesso negado para esse recurso\""),
          () -> assertThat(body).contains("\"instance\":\"/api/v1/urls\""));
    }

    @Test
    @DisplayName("Deve serializar propriedades customizadas do ProblemDetail")
    void shouldSerializeCustomProblemDetailProperties() throws Exception {
      // 1. Arrange
      var response = new MockHttpServletResponse();
      var errors =
          List.of(
              Map.of("field", "email", "message", "must be a well-formed email address"),
              Map.of("field", "password", "message", "must have at least 8 characters"));
      var problemDetail =
          ProblemDetail.forStatusAndDetail(
              HttpStatus.BAD_REQUEST, CommonErrorCode.REQUEST_VALIDATION_FAILED.getMessage());
      problemDetail.setTitle("Validação");
      problemDetail.setType(URI.create(ProblemType.VALIDATION));
      problemDetail.setProperty("errorCode", CommonErrorCode.REQUEST_VALIDATION_FAILED.getCode());
      problemDetail.setProperty("errors", errors);

      // 2. Act
      writer.write(response, problemDetail);

      // 3. Assert
      var body = response.getContentAsString(StandardCharsets.UTF_8);

      assertAll(
          () -> assertThat(body).contains("\"errorCode\":\"REQUEST_VALIDATION_FAILED\""),
          () -> assertThat(body).contains("\"errors\""),
          () -> assertThat(body).contains("\"field\":\"email\""),
          () -> assertThat(body).contains("\"message\":\"must be a well-formed email address\""),
          () -> assertThat(body).contains("\"field\":\"password\""),
          () -> assertThat(body).contains("\"message\":\"must have at least 8 characters\""));
    }
  }
}
