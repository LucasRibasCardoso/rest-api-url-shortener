package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import com.app.url_shortener.url.infrastructure.config.RedirectCacheProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador Redis de Cache de Redirect")
class RedirectCacheAdapterUnitTest {

  private static final Duration TTL_ACTIVE = Duration.ofMinutes(15);
  private static final Duration TTL_DELETED = Duration.ofHours(1);
  private static final Duration TTL_NOT_FOUND = Duration.ofMinutes(5);

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private ValueOperations<String, String> valueOperations;

  @Nested
  @DisplayName("Falhas de infraestrutura")
  class InfrastructureFailureTests {

    @Test
    @DisplayName("Deve traduzir falha do Redis ao ler cache")
    void shouldTranslateRedisFailureWhenFindingByShortCode() {
      // 1. Arrange
      var adapter = adapter();
      var shortCode = "aB3dE";
      var exception = new DataAccessResourceFailureException("Redis unavailable");
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.get("url:redirect:" + shortCode)).thenThrow(exception);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.findByShortCode(shortCode))
          .isInstanceOf(RedirectCacheException.class)
          .hasCause(exception);
      verify(redisTemplate).opsForValue();
      verify(valueOperations).get("url:redirect:" + shortCode);
      verifyNoMoreInteractions(redisTemplate, valueOperations);
    }

    @Test
    @DisplayName("Deve traduzir falha do Redis ao salvar ACTIVE")
    void shouldTranslateRedisFailureWhenSavingActive() {
      // 1. Arrange
      var adapter = adapter();
      var shortCode = "aB3dE";
      var exception = new DataAccessResourceFailureException("Redis unavailable");
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      doThrow(exception)
          .when(valueOperations)
          .set(org.mockito.ArgumentMatchers.eq("url:redirect:" + shortCode),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.eq(TTL_ACTIVE));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.saveActive(shortCode, "https://example.com"))
          .isInstanceOf(RedirectCacheException.class)
          .hasCause(exception);
      verify(redisTemplate).opsForValue();
      verify(valueOperations)
          .set(org.mockito.ArgumentMatchers.eq("url:redirect:" + shortCode),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.eq(TTL_ACTIVE));
      verifyNoMoreInteractions(redisTemplate, valueOperations);
    }

    @Test
    @DisplayName("Deve traduzir falha do Redis ao salvar DELETED")
    void shouldTranslateRedisFailureWhenSavingDeleted() {
      // 1. Arrange
      var adapter = adapter();
      var shortCode = "aB3dE";
      var exception = new DataAccessResourceFailureException("Redis unavailable");
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      doThrow(exception)
          .when(valueOperations)
          .set(org.mockito.ArgumentMatchers.eq("url:redirect:" + shortCode),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.eq(TTL_DELETED));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.saveDeleted(shortCode))
          .isInstanceOf(RedirectCacheException.class)
          .hasCause(exception);
      verify(redisTemplate).opsForValue();
      verify(valueOperations)
          .set(org.mockito.ArgumentMatchers.eq("url:redirect:" + shortCode),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.eq(TTL_DELETED));
      verifyNoMoreInteractions(redisTemplate, valueOperations);
    }

    @Test
    @DisplayName("Deve traduzir falha do Redis ao salvar NOT_FOUND")
    void shouldTranslateRedisFailureWhenSavingNotFound() {
      // 1. Arrange
      var adapter = adapter();
      var shortCode = "aB3dE";
      var exception = new DataAccessResourceFailureException("Redis unavailable");
      when(redisTemplate.opsForValue()).thenReturn(valueOperations);
      when(valueOperations.setIfAbsent(
              org.mockito.ArgumentMatchers.eq("url:redirect:" + shortCode),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.eq(TTL_NOT_FOUND)))
          .thenThrow(exception);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.saveNotFoundIfAbsent(shortCode))
          .isInstanceOf(RedirectCacheException.class)
          .hasCause(exception);
      verify(redisTemplate).opsForValue();
      verify(valueOperations)
          .setIfAbsent(org.mockito.ArgumentMatchers.eq("url:redirect:" + shortCode),
              org.mockito.ArgumentMatchers.anyString(),
              org.mockito.ArgumentMatchers.eq(TTL_NOT_FOUND));
      verifyNoMoreInteractions(redisTemplate, valueOperations);
    }

    @Test
    @DisplayName("Deve traduzir falha do Redis ao remover cache")
    void shouldTranslateRedisFailureWhenEvicting() {
      // 1. Arrange
      var adapter = adapter();
      var shortCode = "aB3dE";
      var exception = new DataAccessResourceFailureException("Redis unavailable");
      doThrow(exception).when(redisTemplate).delete("url:redirect:" + shortCode);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.evict(shortCode))
          .isInstanceOf(RedirectCacheException.class)
          .hasCause(exception);
      verify(redisTemplate).delete("url:redirect:" + shortCode);
      verifyNoMoreInteractions(redisTemplate, valueOperations);
    }
  }

  private RedirectCacheAdapter adapter() {
    return new RedirectCacheAdapter(
        new RedirectCacheProperties(
            new RedirectCacheProperties.Ttl(TTL_ACTIVE, TTL_DELETED, TTL_NOT_FOUND)),
        new ObjectMapper(),
        redisTemplate);
  }
}
