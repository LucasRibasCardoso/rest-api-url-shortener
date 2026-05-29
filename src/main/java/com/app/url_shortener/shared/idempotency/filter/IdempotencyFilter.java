package com.app.url_shortener.shared.idempotency.filter;

import com.app.url_shortener.shared.idempotency.config.IdempotencyProperties;
import com.app.url_shortener.shared.idempotency.exception.IdempotencyConflictException;
import com.app.url_shortener.shared.idempotency.exception.IdempotencyHeaderMissingException;
import com.app.url_shortener.shared.idempotency.impl.CachedBodyHttpServletRequest;
import com.app.url_shortener.shared.idempotency.impl.PrincipalScopeResolver;
import com.app.url_shortener.shared.idempotency.impl.RequestBodyHasher;
import com.app.url_shortener.shared.idempotency.port.IdempotencyPort;
import com.app.url_shortener.shared.idempotency.valueobjects.CachedResponse;
import com.app.url_shortener.shared.idempotency.valueobjects.IdempotencyEntry;
import com.app.url_shortener.shared.idempotency.valueobjects.IdempotencyKey;
import com.app.url_shortener.shared.idempotency.valueobjects.RequestFingerprint;
import jakarta.servlet.ServletException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;
import org.springframework.util.StreamUtils;
import org.springframework.web.util.ContentCachingResponseWrapper;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

  private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
  private static final Duration IN_PROGRESS_TTL = Duration.ofMinutes(2);
  private static final Duration COMPLETED_TTL = Duration.ofHours(24);

  private static final Set<String> IDEMPOTENCY_SUPPORTED_METHODS = Set.of(
          HttpMethod.POST.name(),
          HttpMethod.PUT.name(),
          HttpMethod.PATCH.name()
  );

  private final IdempotencyPort idempotencyPort;
  private final RequestBodyHasher requestBodyHasher;
  private final HandlerExceptionResolver exceptionResolver;
  private final IdempotencyProperties idempotencyProperties;
  private final PrincipalScopeResolver principalScopeResolver;
  private final PathMatcher pathMatcher = new AntPathMatcher();

  public IdempotencyFilter(
          IdempotencyPort idempotencyPort,
          @Qualifier("handlerExceptionResolver")
          HandlerExceptionResolver exceptionResolver,
          IdempotencyProperties idempotencyProperties,
          PrincipalScopeResolver principalScopeResolver,
          RequestBodyHasher requestBodyHasher) {
    this.idempotencyPort = idempotencyPort;
    this.exceptionResolver = exceptionResolver;
    this.idempotencyProperties = idempotencyProperties;
    this.principalScopeResolver = principalScopeResolver;
    this.requestBodyHasher = requestBodyHasher;
  }

  @Override
  public boolean shouldNotFilter(HttpServletRequest request) {
    if (!IDEMPOTENCY_SUPPORTED_METHODS.contains(request.getMethod())) {
      return true;
    }

    List<String> protectedUris = idempotencyProperties.protectedUris();

    if (protectedUris == null || protectedUris.isEmpty()) {
      return true;
    }

    String requestUri = request.getRequestURI();
    boolean matchesProtectedUri = protectedUris.stream().anyMatch(pattern -> pathMatcher.match(pattern, requestUri));

    return !matchesProtectedUri;
  }

  @Override
  public void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain)
      throws ServletException, IOException {

    String idempotencyKeyValue = request.getHeader(IDEMPOTENCY_KEY_HEADER);
    if (idempotencyKeyValue == null || idempotencyKeyValue.isBlank()) {
      exceptionResolver.resolveException(request, response, null, new IdempotencyHeaderMissingException());
      return;
    }

    byte[] requestBody = StreamUtils.copyToByteArray(request.getInputStream());
    var cachedRequest = new CachedBodyHttpServletRequest(request, requestBody);
    var fingerprint = RequestFingerprint.of(
            principalScopeResolver.resolve(request),
            request.getMethod(),
            request.getRequestURI(),
            requestBodyHasher.sha256Hex(requestBody)
    );

    IdempotencyKey idempotencyKey = IdempotencyKey.generate(idempotencyKeyValue, fingerprint);
    boolean savedInProgress = idempotencyPort.saveInProgress(idempotencyKey, IN_PROGRESS_TTL);

    if (!savedInProgress) {
      Optional<IdempotencyEntry> existingEntry = idempotencyPort.find(idempotencyKey);

      if (existingEntry.isPresent() && existingEntry.get().isComplete()) {
        restoreCachedResponse(request, response, fingerprint, existingEntry.get());
        return;
      }

      exceptionResolver.resolveException(request, response, null, new IdempotencyConflictException());
      return;
    }

    var wrappedResponse = new ContentCachingResponseWrapper(response);

    try {
      filterChain.doFilter(cachedRequest, wrappedResponse);

      if (wrappedResponse.getStatus() >= HttpServletResponse.SC_INTERNAL_SERVER_ERROR) {
        idempotencyPort.delete(idempotencyKey);
        return;
      }

      var responseBody = new String(wrappedResponse.getContentAsByteArray(), responseCharset(wrappedResponse));
      var cachedResponse = new CachedResponse(wrappedResponse.getStatus(), responseBody);
      idempotencyPort.saveCompleted(idempotencyKey, cachedResponse, COMPLETED_TTL);
    }
    catch (Exception exception) {
      idempotencyPort.delete(idempotencyKey);
      throw exception;
    }
    finally {
      wrappedResponse.copyBodyToResponse();
    }
  }

  private void restoreCachedResponse(
      HttpServletRequest request,
      HttpServletResponse response,
      RequestFingerprint currentFingerprint,
      IdempotencyEntry entry
  ) throws IOException {
    if (!currentFingerprint.equals(entry.fingerprint()) || entry.response() == null) {
      exceptionResolver.resolveException(request, response, null, new IdempotencyConflictException());
      return;
    }

    response.setStatus(entry.response().status());
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write(entry.response().body());
  }

  private static Charset responseCharset(HttpServletResponse response) {
    String characterEncoding = response.getCharacterEncoding();
    if (characterEncoding == null || characterEncoding.isBlank()) {
      return StandardCharsets.UTF_8;
    }
    return Charset.forName(characterEncoding);
  }

}
