package com.app.url_shortener.security.exception.handler;

import com.app.url_shortener.shared.exception.CommonErrorCode;
import com.app.url_shortener.shared.error.ProblemDetailFactory;
import com.app.url_shortener.shared.error.ProblemDetailResponseWriter;
import com.app.url_shortener.shared.error.ProblemType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.access.AccessDeniedException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - CustomAccessDeniedHandler")
class CustomAccessDeniedHandlerTest {

  @Mock
  private ProblemDetailFactory problemDetailFactory;

  @Mock
  private ProblemDetailResponseWriter responseWriter;

  @InjectMocks
  private CustomAccessDeniedHandler handler;

  @Nested
  @DisplayName("Handle")
  class HandleTests {

    @Test
    @DisplayName("Deve criar ProblemDetail de acesso negado e escrever na resposta")
    void shouldCreateForbiddenProblemDetailAndWriteResponse() throws Exception {
      // 1. Arrange
      var request = new MockHttpServletRequest("POST", "/api/v1/admin/reports");
      var response = new MockHttpServletResponse();
      var accessDeniedException = new AccessDeniedException("Denied");
      var problemDetail = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);

      given(problemDetailFactory.createWithInstance(
              HttpStatus.FORBIDDEN,
              "Acesso negado",
              CommonErrorCode.AUTH_ACCESS_DENIED.getMessage(),
              ProblemType.FORBIDDEN,
              CommonErrorCode.AUTH_ACCESS_DENIED,
              request.getRequestURI()
      )).willReturn(problemDetail);

      // 2. Act
      handler.handle(request, response, accessDeniedException);

      // 3. Assert
      verify(problemDetailFactory).createWithInstance(
              HttpStatus.FORBIDDEN,
              "Acesso negado",
              CommonErrorCode.AUTH_ACCESS_DENIED.getMessage(),
              ProblemType.FORBIDDEN,
              CommonErrorCode.AUTH_ACCESS_DENIED,
              "/api/v1/admin/reports"
      );
      verify(responseWriter).write(response, problemDetail);
      verifyNoMoreInteractions(problemDetailFactory, responseWriter);
    }
  }
}
