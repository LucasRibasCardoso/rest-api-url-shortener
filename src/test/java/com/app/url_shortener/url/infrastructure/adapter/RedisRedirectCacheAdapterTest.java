package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.config.BaseRedisSliceTest;
import com.app.url_shortener.url.application.result.RedirectCacheStatus;
import com.app.url_shortener.url.application.result.UrlRedirectCacheEntry;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("redis-slice")
@Import({
    RedisRedirectCacheAdapter.class,
    RedisRedirectCacheAdapterObjectMapperTestConfig.class
})
@TestPropertySource(properties = {
    "app.url.redirect-cache.ttl.active=15m",
    "app.url.redirect-cache.ttl.deleted=1h",
    "app.url.redirect-cache.ttl.not-found=5m"
})
@DisplayName("Slice Redis - Cache de Redirect de URL")
class RedisRedirectCacheAdapterTest extends BaseRedisSliceTest {

  private static final String KEY_PREFIX = "url:redirect:";
  private static final String KEY_PATTERN = KEY_PREFIX + "*";

  @Autowired
  private RedisRedirectCacheAdapter adapter;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    deleteRedirectKeys();
  }

  @AfterEach
  void tearDown() {
    deleteRedirectKeys();
  }

  @Nested
  @DisplayName("Leitura")
  class FindTests {

    @Test
    @DisplayName("Deve retornar Optional vazio quando chave não existir")
    void shouldReturnEmptyWhenKeyDoesNotExist() {
      // 1. Arrange
      var shortCode = "missing";

      // 2. Act
      var result = adapter.findByShortCode(shortCode);

      // 3. Assert
      assertThat(result).isEmpty();
      assertThat(redisTemplate.hasKey(redisKey(shortCode))).isFalse();
    }

    @Test
    @DisplayName("Deve desserializar entrada salva como JSON no Redis")
    void shouldDeserializeEntryFromRedisJson() {
      // 1. Arrange
      var shortCode = "jsonActive";
      var entry = new UrlRedirectCacheEntry(RedirectCacheStatus.ACTIVE, "https://example.com");
      redisTemplate.opsForValue().set(redisKey(shortCode), objectMapper.writeValueAsString(entry));

      // 2. Act
      var result = adapter.findByShortCode(shortCode);

      // 3. Assert
      assertThat(result).contains(entry);
    }

    @Test
    @DisplayName("Deve lançar RedirectCacheException quando JSON estiver inválido")
    void shouldThrowRedirectCacheExceptionWhenJsonIsInvalid() {
      // 1. Arrange
      var shortCode = "badJson";
      redisTemplate.opsForValue().set(redisKey(shortCode), "{invalid-json");

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.findByShortCode(shortCode))
          .isInstanceOf(RedirectCacheException.class);
    }
  }

  @Nested
  @DisplayName("Armazenamento ACTIVE")
  class ActiveStorageTests {

    @Test
    @DisplayName("Deve salvar ACTIVE somente quando chave não existir")
    void shouldSaveActiveIfKeyDoesNotExist() {
      // 1. Arrange
      var shortCode = "activeNew";
      var longUrl = "https://example.com";

      // 2. Act
      var result = adapter.saveActiveIfAbsent(shortCode, longUrl);

      // 3. Assert
      assertThat(result).isTrue();
      assertThat(adapter.findByShortCode(shortCode))
          .contains(new UrlRedirectCacheEntry(RedirectCacheStatus.ACTIVE, longUrl));
      assertThatTtlIsCloseTo(redisKey(shortCode), Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("Deve preservar DELETED quando saveActiveIfAbsent encontrar chave existente")
    void shouldPreserveDeletedWhenSaveActiveIfAbsentFindsExistingKey() {
      // 1. Arrange
      var shortCode = "activeExistingDeleted";
      adapter.saveDeleted(shortCode);

      // 2. Act
      var result = adapter.saveActiveIfAbsent(shortCode, "https://example.com");

      // 3. Assert
      assertThat(result).isFalse();
      assertThat(adapter.findByShortCode(shortCode))
          .contains(new UrlRedirectCacheEntry(RedirectCacheStatus.DELETED, null));
    }

    @Test
    @DisplayName("Deve sobrescrever NOT_FOUND com ACTIVE em saveActive")
    void shouldOverwriteNotFoundWithActiveWhenSavingActive() {
      // 1. Arrange
      var shortCode = "activeOverwrite";
      var longUrl = "https://example.com";
      adapter.saveNotFoundIfAbsent(shortCode);

      // 2. Act
      adapter.saveActive(shortCode, longUrl);

      // 3. Assert
      assertThat(adapter.findByShortCode(shortCode))
          .contains(new UrlRedirectCacheEntry(RedirectCacheStatus.ACTIVE, longUrl));
      assertThatTtlIsCloseTo(redisKey(shortCode), Duration.ofMinutes(15));
    }
  }

  @Nested
  @DisplayName("Armazenamento NOT_FOUND")
  class NotFoundStorageTests {

    @Test
    @DisplayName("Deve salvar NOT_FOUND somente quando chave não existir")
    void shouldSaveNotFoundIfKeyDoesNotExist() {
      // 1. Arrange
      var shortCode = "notFoundNew";

      // 2. Act
      var result = adapter.saveNotFoundIfAbsent(shortCode);

      // 3. Assert
      assertThat(result).isTrue();
      assertThat(adapter.findByShortCode(shortCode))
          .contains(new UrlRedirectCacheEntry(RedirectCacheStatus.NOT_FOUND, null));
      assertThatTtlIsCloseTo(redisKey(shortCode), Duration.ofMinutes(5));
    }

    @Test
    @DisplayName("Deve preservar ACTIVE quando saveNotFoundIfAbsent encontrar chave existente")
    void shouldPreserveActiveWhenSaveNotFoundIfAbsentFindsExistingKey() {
      // 1. Arrange
      var shortCode = "notFoundExistingActive";
      var longUrl = "https://example.com";
      adapter.saveActive(shortCode, longUrl);

      // 2. Act
      var result = adapter.saveNotFoundIfAbsent(shortCode);

      // 3. Assert
      assertThat(result).isFalse();
      assertThat(adapter.findByShortCode(shortCode))
          .contains(new UrlRedirectCacheEntry(RedirectCacheStatus.ACTIVE, longUrl));
    }
  }

  @Nested
  @DisplayName("Armazenamento DELETED")
  class DeletedStorageTests {

    @Test
    @DisplayName("Deve sobrescrever ACTIVE com DELETED")
    void shouldOverwriteActiveWithDeleted() {
      // 1. Arrange
      var shortCode = "deletedActive";
      adapter.saveActive(shortCode, "https://example.com");

      // 2. Act
      adapter.saveDeleted(shortCode);

      // 3. Assert
      assertThat(adapter.findByShortCode(shortCode))
          .contains(new UrlRedirectCacheEntry(RedirectCacheStatus.DELETED, null));
      assertThatTtlIsCloseTo(redisKey(shortCode), Duration.ofHours(1));
    }

    @Test
    @DisplayName("Deve sobrescrever NOT_FOUND com DELETED")
    void shouldOverwriteNotFoundWithDeleted() {
      // 1. Arrange
      var shortCode = "deletedNotFound";
      adapter.saveNotFoundIfAbsent(shortCode);

      // 2. Act
      adapter.saveDeleted(shortCode);

      // 3. Assert
      assertThat(adapter.findByShortCode(shortCode))
          .contains(new UrlRedirectCacheEntry(RedirectCacheStatus.DELETED, null));
      assertThatTtlIsCloseTo(redisKey(shortCode), Duration.ofHours(1));
    }
  }

  @Nested
  @DisplayName("Remoção")
  class EvictTests {

    @Test
    @DisplayName("Deve remover chave do Redis")
    void shouldRemoveRedisKey() {
      // 1. Arrange
      var shortCode = "evictMe";
      var redisKey = redisKey(shortCode);
      adapter.saveActive(shortCode, "https://example.com");

      // 2. Act
      adapter.evict(shortCode);

      // 3. Assert
      assertThat(redisTemplate.hasKey(redisKey)).isFalse();
      assertThat(redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS)).isEqualTo(-2L);
    }
  }

  private void assertThatTtlIsCloseTo(String redisKey, Duration expectedTtl) {
    Long ttlMillis = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
    assertThat(ttlMillis)
        .isNotNull()
        .isPositive()
        .isLessThanOrEqualTo(expectedTtl.toMillis())
        .isGreaterThan(expectedTtl.minusSeconds(5).toMillis());
  }

  private void deleteRedirectKeys() {
    Set<String> keys = redisTemplate.keys(KEY_PATTERN);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  private static String redisKey(String shortCode) {
    return KEY_PREFIX + shortCode;
  }
}

@TestConfiguration
class RedisRedirectCacheAdapterObjectMapperTestConfig {

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
