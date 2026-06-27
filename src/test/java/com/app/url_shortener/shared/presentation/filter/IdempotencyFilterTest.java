package com.app.url_shortener.shared.presentation.filter;

import com.app.url_shortener.shared.idempotency.config.IdempotencyProperties;
import com.app.url_shortener.shared.idempotency.exception.IdempotencyConflictException;
import com.app.url_shortener.shared.idempotency.exception.IdempotencyHeaderMissingException;
import com.app.url_shortener.shared.idempotency.enums.IdempotencyStatus;
import com.app.url_shortener.shared.idempotency.filter.IdempotencyFilter;
import com.app.url_shortener.shared.idempotency.impl.PrincipalScopeResolver;
import com.app.url_shortener.shared.idempotency.impl.RequestBodyHasher;
import com.app.url_shortener.shared.idempotency.port.IdempotencyPort;
import com.app.url_shortener.shared.idempotency.valueobjects.CachedResponse;
import com.app.url_shortener.shared.idempotency.valueobjects.IdempotencyEntry;
import com.app.url_shortener.shared.idempotency.valueobjects.IdempotencyKey;
import com.app.url_shortener.shared.idempotency.valueobjects.RequestFingerprint;
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
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - IdempotencyFilter")
class IdempotencyFilterTest {

  private static final String IDEMPOTENCY_KEY = "request-key-123";
  private static final String PROTECTED_URI = "/api/v1/urls";
  private static final RequestFingerprint FINGERPRINT = new RequestFingerprint(
          "ip:127.0.0.1",
          "POST",
          PROTECTED_URI,
          "body-hash"
  );
  private static final IdempotencyKey GENERATED_KEY = IdempotencyKey.generate(IDEMPOTENCY_KEY, FINGERPRINT);

  @Mock
  private IdempotencyPort idempotencyPort;

  @Mock
  private PrincipalScopeResolver principalScopeResolver;

  @Mock
  private RequestBodyHasher requestBodyHasher;

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
      var request = request("POST", "/api/v1/users");
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
      var request = request("POST", PROTECTED_URI);
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

      verifyNoInteractions(idempotencyPort, principalScopeResolver, requestBodyHasher);
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
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI, "");
      var response = new MockHttpServletResponse();
      var filterChain = new MockFilterChain();
      var cachedResponse =
          new CachedResponse(HttpServletResponse.SC_CREATED, "{\"message\":\"código criado\"}");
      var entry = new IdempotencyEntry(IdempotencyStatus.COMPLETED, FINGERPRINT, cachedResponse, Instant.now());

      given(principalScopeResolver.resolve(request)).willReturn(FINGERPRINT.principalScope());
      given(requestBodyHasher.sha256Hex(any(byte[].class))).willReturn(FINGERPRINT.bodyHash());
      given(idempotencyPort.saveInProgress(GENERATED_KEY, Duration.ofMinutes(2))).willReturn(false);
      given(idempotencyPort.find(GENERATED_KEY)).willReturn(Optional.of(entry));

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      assertAll(
              () -> assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CREATED),
              () -> assertThat(response.getContentType()).isEqualTo("application/json;charset=UTF-8"),
              () -> assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8"),
              () -> assertThat(response.getContentAsString()).isEqualTo(cachedResponse.body()),
              () -> assertThat(filterChain.getRequest()).isNull()
      );

      verify(principalScopeResolver).resolve(request);
      verify(requestBodyHasher).sha256Hex(any(byte[].class));
      verify(idempotencyPort).saveInProgress(GENERATED_KEY, Duration.ofMinutes(2));
      verify(idempotencyPort).find(GENERATED_KEY);
      verifyNoInteractions(exceptionResolver);
      verifyNoMoreInteractions(idempotencyPort, principalScopeResolver, requestBodyHasher);
    }

    @Test
    @DisplayName("Deve resolver conflito quando requisição estiver em processamento")
    void shouldResolveConflictWhenRequestIsInProgress() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI, "");
      var response = new MockHttpServletResponse();
      var filterChain = new MockFilterChain();

      given(principalScopeResolver.resolve(request)).willReturn(FINGERPRINT.principalScope());
      given(requestBodyHasher.sha256Hex(any(byte[].class))).willReturn(FINGERPRINT.bodyHash());
      given(idempotencyPort.saveInProgress(GENERATED_KEY, Duration.ofMinutes(2))).willReturn(false);
      given(idempotencyPort.find(GENERATED_KEY)).willReturn(Optional.empty());

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

      verify(idempotencyPort).saveInProgress(GENERATED_KEY, Duration.ofMinutes(2));
      verify(idempotencyPort).find(GENERATED_KEY);
      verifyNoMoreInteractions(idempotencyPort, exceptionResolver);
    }
  }

  @Nested
  @DisplayName("Nova requisição")
  class NewRequestTests {

    @Test
    @DisplayName("Deve executar filter chain e salvar resposta concluída com sucesso")
    void shouldCallFilterChainAndSaveCompletedResponseWhenRequestSucceeds() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI, "");
      var response = new MockHttpServletResponse();
      var responseBody = "{\"id\":\"url-123\"}";
      var filterChain = filterChainReturning(HttpServletResponse.SC_CREATED, responseBody);

      given(principalScopeResolver.resolve(request)).willReturn(FINGERPRINT.principalScope());
      given(requestBodyHasher.sha256Hex(any(byte[].class))).willReturn(FINGERPRINT.bodyHash());
      given(idempotencyPort.saveInProgress(GENERATED_KEY, Duration.ofMinutes(2))).willReturn(true);

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      verify(idempotencyPort).saveInProgress(GENERATED_KEY, Duration.ofMinutes(2));
      verify(idempotencyPort).saveCompleted(
              eq(GENERATED_KEY),
              cachedResponseCaptor.capture(),
              eq(Duration.ofHours(24))
      );
      verify(idempotencyPort, never()).delete(GENERATED_KEY);
      verifyNoInteractions(exceptionResolver);
      verifyNoMoreInteractions(idempotencyPort);

      var cachedResponse = cachedResponseCaptor.getValue();

      assertAll(
              () -> assertThat(filterChain.getRequest()).isNotSameAs(request),
              () -> assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_CREATED),
              () -> assertThat(response.getContentAsString()).isEqualTo(responseBody),
              () -> assertThat(cachedResponse.status()).isEqualTo(HttpServletResponse.SC_CREATED),
              () -> assertThat(cachedResponse.body()).isEqualTo(responseBody)
      );
    }

    @Test
    @DisplayName("Deve armazenar resposta JSON UTF-8 sem corromper caracteres acentuados")
    void shouldCacheUtf8JsonResponseWithoutCorruptingAccentedCharacters() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI, "");
      var response = new MockHttpServletResponse();
      var responseBody = "{\"message\":\"código de verificação\"}";
      var filterChain = filterChainReturningUtf8Json(HttpServletResponse.SC_CREATED, responseBody);

      given(principalScopeResolver.resolve(request)).willReturn(FINGERPRINT.principalScope());
      given(requestBodyHasher.sha256Hex(any(byte[].class))).willReturn(FINGERPRINT.bodyHash());
      given(idempotencyPort.saveInProgress(GENERATED_KEY, Duration.ofMinutes(2))).willReturn(true);

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      verify(idempotencyPort)
          .saveCompleted(
              eq(GENERATED_KEY), cachedResponseCaptor.capture(), eq(Duration.ofHours(24)));
      assertThat(cachedResponseCaptor.getValue().body()).isEqualTo(responseBody);
    }

    @Test
    @DisplayName("Deve excluir chave de idempotência quando resposta tiver erro 4xx")
    void shouldDeleteIdempotencyKeyWhenResponseHasClientError() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI, "");
      var response = new MockHttpServletResponse();
      var filterChain = filterChainReturning(HttpServletResponse.SC_BAD_REQUEST, "validation error");

      given(principalScopeResolver.resolve(request)).willReturn(FINGERPRINT.principalScope());
      given(requestBodyHasher.sha256Hex(any(byte[].class))).willReturn(FINGERPRINT.bodyHash());
      given(idempotencyPort.saveInProgress(GENERATED_KEY, Duration.ofMinutes(2))).willReturn(true);

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      assertAll(
              () -> assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_BAD_REQUEST),
              () -> assertThat(response.getContentAsString()).isEqualTo("validation error")
      );

      verify(idempotencyPort).saveInProgress(GENERATED_KEY, Duration.ofMinutes(2));
      verify(idempotencyPort).delete(GENERATED_KEY);
      verify(idempotencyPort, never()).saveCompleted(any(), any(), any());
      verifyNoInteractions(exceptionResolver);
      verifyNoMoreInteractions(idempotencyPort);
    }

    @Test
    @DisplayName("Deve excluir chave de idempotência quando resposta tiver erro 5xx")
    void shouldDeleteIdempotencyKeyWhenResponseHasServerError() throws Exception {
      // 1. Arrange
      var request = requestWithIdempotencyKey("POST", PROTECTED_URI, "");
      var response = new MockHttpServletResponse();
      var filterChain = filterChainReturning(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "server error");

      given(principalScopeResolver.resolve(request)).willReturn(FINGERPRINT.principalScope());
      given(requestBodyHasher.sha256Hex(any(byte[].class))).willReturn(FINGERPRINT.bodyHash());
      given(idempotencyPort.saveInProgress(GENERATED_KEY, Duration.ofMinutes(2))).willReturn(true);

      // 2. Act
      filter.doFilterInternal(request, response, filterChain);

      // 3. Assert
      assertAll(
              () -> assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_INTERNAL_SERVER_ERROR),
              () -> assertThat(response.getContentAsString()).isEqualTo("server error")
      );

      verify(idempotencyPort).saveInProgress(GENERATED_KEY, Duration.ofMinutes(2));
      verify(idempotencyPort).delete(GENERATED_KEY);
      verify(idempotencyPort, never()).saveCompleted(any(), any(), any());
      verifyNoInteractions(exceptionResolver);
      verifyNoMoreInteractions(idempotencyPort);
    }
  }

  private static MockHttpServletRequest request(String method, String uri) {
    return new MockHttpServletRequest(method, uri);
  }

  private static MockHttpServletRequest requestWithIdempotencyKey(String method, String uri, String body) {
    var request = request(method, uri);
    request.addHeader("Idempotency-Key", IDEMPOTENCY_KEY);
    request.setContent(body.getBytes(StandardCharsets.UTF_8));
    return request;
  }

  private static MockFilterChain filterChainReturning(int status, String body) {
    return new MockFilterChain(new HttpServlet() {

      @Override
      protected void service(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException {
        response.setStatus(status);
        response.getWriter().write(body);
      }
    });
  }

  private static MockFilterChain filterChainReturningUtf8Json(int status, String body) {
    return new MockFilterChain(new HttpServlet() {

      @Override
      protected void service(HttpServletRequest request, HttpServletResponse response)
          throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getOutputStream().write(body.getBytes(StandardCharsets.UTF_8));
      }
    });
  }
}
