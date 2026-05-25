package com.app.url_shortener.shared.presentation.filter;

import com.app.url_shortener.shared.config.properties.IdempotencyProperties;
import com.app.url_shortener.shared.exception.conflict.IdempotencyConflictException;
import com.app.url_shortener.shared.exception.internalservererror.IdempotencyCacheException;
import com.app.url_shortener.shared.exception.validation.IdempotencyHeaderMissingException;
import com.app.url_shortener.shared.infrastructure.idempotency.CachedResponse;
import com.app.url_shortener.shared.infrastructure.idempotency.IdempotencyStore;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.util.ContentCachingResponseWrapper;

import java.io.IOException;

@Component
public class IdempotencyFilter extends OncePerRequestFilter {

  private static final long IN_PROGRESS_TIMEOUT_MINUTES = 2L;
  private static final long COMPLETED_TTL_HOURS = 24L;

  private final IdempotencyStore idempotencyStore;
  private final HandlerExceptionResolver exceptionResolver;
  private final IdempotencyProperties idempotencyProperties;

  public IdempotencyFilter(
          IdempotencyStore idempotencyStore,
          @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver,
          IdempotencyProperties idempotencyProperties) {
    this.idempotencyStore = idempotencyStore;
    this.exceptionResolver = exceptionResolver;
    this.idempotencyProperties = idempotencyProperties;
  }

  @Override
  protected boolean shouldNotFilter(HttpServletRequest request) {
    return idempotencyProperties.protectedUris().stream().noneMatch(uri -> request.getRequestURI().startsWith(uri));
  }

  @Override
  protected void doFilterInternal(
          HttpServletRequest request,
          HttpServletResponse response,
          FilterChain filterChain) throws IOException {
    String idempotencyKey = request.getHeader("Idempotency-Key");

    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      exceptionResolver.resolveException(request, response, null, new IdempotencyHeaderMissingException());
      return;
    }

    boolean isNewRequest = idempotencyStore.saveInProgress(idempotencyKey, IN_PROGRESS_TIMEOUT_MINUTES);

    if (!isNewRequest) {
      CachedResponse cachedResponse;
      try {
        cachedResponse = idempotencyStore.getState(idempotencyKey);
      } catch (IdempotencyCacheException e) {
        exceptionResolver.resolveException(request, response, null, e);
        return;
      }

      // Cenário concorrência
      if (cachedResponse == null) {
        exceptionResolver.resolveException(request, response, null, new IdempotencyConflictException());
        return;
      }

      // Cenário Cache Hit
      response.setStatus(cachedResponse.status());
      response.setContentType("application/json");
      response.getOutputStream().write(cachedResponse.body());
      return;
    }

    // Cenário nova request
    ContentCachingResponseWrapper cachingResponse = new ContentCachingResponseWrapper(response);

    try {
      filterChain.doFilter(request, cachingResponse);

      int status = cachingResponse.getStatus();

      if (status >= 500) {
        idempotencyStore.delete(idempotencyKey);
      } else {
        byte[] responseBody = cachingResponse.getContentAsByteArray();
        idempotencyStore.saveCompleted(idempotencyKey, new CachedResponse(status, responseBody), COMPLETED_TTL_HOURS);
      }

    } catch (Exception e) {
      idempotencyStore.delete(idempotencyKey);
      exceptionResolver.resolveException(request, cachingResponse, null, e);

    } finally {
      cachingResponse.copyBodyToResponse();
    }
  }
}
