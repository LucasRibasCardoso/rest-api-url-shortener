package com.app.url_shortener.shared.presentation.filter;

import com.app.url_shortener.shared.config.properties.IdempotencyProperties;
import com.app.url_shortener.shared.exception.conflict.IdempotencyConflictException;
import com.app.url_shortener.shared.exception.validation.IdempotencyHeaderMissingException;
import com.app.url_shortener.shared.infrastructure.idempotency.CachedResponse;
import com.app.url_shortener.shared.infrastructure.idempotency.IdempotencyStore;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.same;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - IdempotencyFilter")
class IdempotencyFilterTest {

  private static final String IDEMPOTENCY_KEY = "request-key-123";
  private static final String PROTECTED_URI = "/api/v1/urls";

  @Mock
  private IdempotencyStore idempotencyStore;

  @Mock
  private HandlerExceptionResolver exceptionResolver;

  @Mock
  private IdempotencyProperties idempotencyProperties;

  @Captor
  private ArgumentCaptor<Exception> exceptionCaptor;

  @Captor
  private ArgumentCaptor<CachedResponse> cachedResponseCaptor;

  @InjectMocks
  private IdempotencyFilter filter;

  @Nested
  @DisplayName("shouldNotFilter")
  class ShouldNotFilterTests {

    @Test
    @DisplayName("Deve retornar true para URI não protegida")
    void shouldReturnTrueForUnprotectedUri() {
      // 1. Arrange
      var request = request("POST", "/api/v1/auth/login");
      given(idempotencyProperties.protectedUris()).willReturn(List.of(PROTECTED_URI));

      // 2. Act
      var shouldNotFilter = filter.shouldNotFilter(request);

      // 3. Assert
      assertThat(shouldNotFilter).isTrue();

      verify(idempotencyProperties).protectedUris();
      verifyNoMoreInteractions(idempotencyProperties);
    }

    @Test
    @DisplayName("Deve retornar false para URI protegida")
    void shouldReturnFalseForProtectedUri() {
      // 1. Arrange
      var request = request("POST", "/api/v1/urls/shorten");
      given(idempotencyProperties.protectedUris()).willReturn(List.of(PROTECTED_URI));

      // 2. Act
      var shouldNotFilter = filter.shouldNotFilter(request);

      // 3. Assert
      assertThat(shouldNotFilter).isFalse();

      verify(idempotencyProperties).protectedUris();
      verifyNoMoreInteractions(idempotencyProperties);
    }
  }

  @Nested
  @DisplayName("Header obrigatório")
  class RequiredHeaderTests {

    @Test
    @DisplayName("Deve resolver exceção quando Idempotency-Key estiver ausente")
    void shouldResolveExceptionWhenIdempotencyKeyIsMissing() throws Exception {
      // 1. Arrange
      var request = request("POST", PROTECTED_URI);
      var response = new MockHttpServletResponse();
      var filterChain = new MockFilterChain();

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      verify(exceptionResolver).resolveException(
              same(request),
              same(response),
              isNull(),
              exceptionCaptor.capture()
      );

      assertThat(exceptionCaptor.getValue()).isInstanceOf(IdempotencyHeaderMissingException.class);
      assertThat(filterChain.getRequest()).isNull();

      verifyNoInteractions(idempotencyStore);
      verifyNoMoreInteractions(exceptionResolver);
    }
  }

  @Nested
  @DisplayName("Requisição existente")
  class ExistingRequestTests {

    @Test
    @DisplayName("Deve restaurar resposta cacheada sem executar o filter chain")
    void shouldRestoreCachedResponseWithoutCallingFilterChain() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI);
      var response = new MockHttpServletResponse();
      var filterChain = new MockFilterChain();
      var cachedBody = "{\"shortCode\":\"abc123\"}".getBytes(StandardCharsets.UTF_8);
      var cachedResponse = new CachedResponse(HttpServletResponse.SC_CREATED, cachedBody);

      given(idempotencyStore.saveInProgress(IDEMPOTENCY_KEY, 2L)).willReturn(false);
      given(idempotencyStore.getState(IDEMPOTENCY_KEY)).willReturn(cachedResponse);

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      assertAll(
              () -> assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CREATED),
              () -> assertThat(response.getContentType()).isEqualTo("application/json"),
              () -> assertThat(response.getContentAsByteArray()).isEqualTo(cachedBody),
              () -> assertThat(filterChain.getRequest()).isNull()
      );

      verify(idempotencyStore).saveInProgress(IDEMPOTENCY_KEY, 2L);
      verify(idempotencyStore).getState(IDEMPOTENCY_KEY);
      verifyNoInteractions(exceptionResolver);
      verifyNoMoreInteractions(idempotencyStore);
    }

    @Test
    @DisplayName("Deve resolver conflito quando requisição estiver em processamento")
    void shouldResolveConflictWhenRequestIsInProgress() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI);
      var response = new MockHttpServletResponse();
      var filterChain = new MockFilterChain();

      given(idempotencyStore.saveInProgress(IDEMPOTENCY_KEY, 2L)).willReturn(false);
      given(idempotencyStore.getState(IDEMPOTENCY_KEY)).willReturn(null);

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      verify(exceptionResolver).resolveException(
              same(request),
              same(response),
              isNull(),
              exceptionCaptor.capture()
      );

      assertThat(exceptionCaptor.getValue()).isInstanceOf(IdempotencyConflictException.class);
      assertThat(filterChain.getRequest()).isNull();

      verify(idempotencyStore).saveInProgress(IDEMPOTENCY_KEY, 2L);
      verify(idempotencyStore).getState(IDEMPOTENCY_KEY);
      verifyNoMoreInteractions(idempotencyStore, exceptionResolver);
    }
  }

  @Nested
  @DisplayName("Nova requisição")
  class NewRequestTests {

    @Test
    @DisplayName("Deve executar filter chain e salvar resposta concluída com sucesso")
    void shouldCallFilterChainAndSaveCompletedResponseWhenRequestSucceeds() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI);
      var response = new MockHttpServletResponse();
      var responseBody = "{\"id\":\"url-123\"}";
      var filterChain = filterChainReturning(HttpServletResponse.SC_CREATED, responseBody);

      given(idempotencyStore.saveInProgress(IDEMPOTENCY_KEY, 2L)).willReturn(true);

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      verify(idempotencyStore).saveInProgress(IDEMPOTENCY_KEY, 2L);
      verify(idempotencyStore).saveCompleted(
              eq(IDEMPOTENCY_KEY),
              cachedResponseCaptor.capture(),
              eq(24L)
      );
      verify(idempotencyStore, never()).delete(any());
      verifyNoInteractions(exceptionResolver);
      verifyNoMoreInteractions(idempotencyStore);

      var cachedResponse = cachedResponseCaptor.getValue();

      assertAll(
              () -> assertThat(filterChain.getRequest()).isSameAs(request),
              () -> assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CREATED),
              () -> assertThat(response.getContentAsString()).isEqualTo(responseBody),
              () -> assertThat(cachedResponse.status()).isEqualTo(HttpServletResponse.SC_CREATED),
              () -> assertThat(cachedResponse.body()).isEqualTo(responseBody.getBytes(StandardCharsets.UTF_8))
      );
    }

    @Test
    @DisplayName("Deve excluir chave de idempotência quando resposta tiver erro 5xx")
    void shouldDeleteIdempotencyKeyWhenResponseHasServerError() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI);
      var response = new MockHttpServletResponse();
      var filterChain = filterChainReturning(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "server error");

      given(idempotencyStore.saveInProgress(IDEMPOTENCY_KEY, 2L)).willReturn(true);

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      assertAll(
              () -> assertThat(filterChain.getRequest()).isSameAs(request),
              () -> assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
              () -> assertThat(response.getContentAsString()).isEqualTo("server error")
      );

      verify(idempotencyStore).saveInProgress(IDEMPOTENCY_KEY, 2L);
      verify(idempotencyStore).delete(IDEMPOTENCY_KEY);
      verify(idempotencyStore, never()).saveCompleted(any(), any(), anyLong());
      verifyNoInteractions(exceptionResolver);
      verifyNoMoreInteractions(idempotencyStore);
    }
  }

  private static MockHttpServletRequest request(String method, String uri) {
    return new MockHttpServletRequest(method, uri);
  }

  private static MockHttpServletRequest requestWithIdempotencyKey(String method, String uri) {
    var request = request(method, uri);
    request.addHeader("Idempotency-Key", IDEMPOTENCY_KEY);
    return request;
  }

  private static MockFilterChain filterChainReturning(int status, String body) {
    return new MockFilterChain(new HttpServlet() {

      @Override
      protected void service(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        response.setStatus(status);
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
      }
    });
  }
}
