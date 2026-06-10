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
import org.springframework.security.core.AuthenticationException;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - CustomAuthenticationEntryPoint")
class CustomAuthenticationEntryPointTest {

  @Mock
  private ProblemDetailFactory problemDetailFactory;

  @Mock
  private ProblemDetailResponseWriter responseWriter;

  @InjectMocks
  private CustomAuthenticationEntryPoint entryPoint;

  @Nested
  @DisplayName("Commence")
  class CommenceTests {

    @Test
    @DisplayName("Deve criar ProblemDetail de não autorizado e escrever na resposta")
    void shouldCreateUnauthorizedProblemDetailAndWriteResponse() throws Exception {
      // 1. Arrange
      var request = new MockHttpServletRequest("GET", "/api/v1/urls");
      var response = new MockHttpServletResponse();
      var authException = mock(AuthenticationException.class);
      var problemDetail = ProblemDetail.forStatus(HttpStatus.UNAUTHORIZED);

      given(problemDetailFactory.createWithInstance(
              HttpStatus.UNAUTHORIZED,
              "Não autorizado",
              CommonErrorCode.AUTH_UNAUTHORIZED.getMessage(),
              ProblemType.UNAUTHORIZED,
              CommonErrorCode.AUTH_UNAUTHORIZED,
              request.getRequestURI()
      )).willReturn(problemDetail);

      // 2. Act
      entryPoint.commence(request, response, authException);

      // 3. Assert
      verify(problemDetailFactory).createWithInstance(
              HttpStatus.UNAUTHORIZED,
              "Não autorizado",
              CommonErrorCode.AUTH_UNAUTHORIZED.getMessage(),
              ProblemType.UNAUTHORIZED,
              CommonErrorCode.AUTH_UNAUTHORIZED,
              "/api/v1/urls"
      );
      verify(responseWriter).write(response, problemDetail);
      verifyNoMoreInteractions(problemDetailFactory, responseWriter);
    }
  }
}
