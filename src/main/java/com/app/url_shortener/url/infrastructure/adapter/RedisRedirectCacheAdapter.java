package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.model.RedirectCacheStatus;
import com.app.url_shortener.url.application.port.output.model.RedirectCacheEntry;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedisRedirectCacheAdapter implements RedirectCachePort {

  private static final String KEY_STRING = "url:redirect:";

  private final Duration ttlActive;
  private final Duration ttlDeleted;
  private final Duration ttlNotFound;
  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redisTemplate;

  public RedisRedirectCacheAdapter(
      @Value("${app.url.redirect-cache.ttl.active}") Duration ttlActive,
      @Value("${app.url.redirect-cache.ttl.deleted}") Duration ttlDeleted,
      @Value("${app.url.redirect-cache.ttl.not-found}") Duration ttlNotFound,
      ObjectMapper objectMapper,
      StringRedisTemplate redisTemplate) {
    this.ttlActive = Objects.requireNonNull(ttlActive, "ttlActive must not be null");
    this.ttlDeleted = Objects.requireNonNull(ttlDeleted, "ttlDeleted must not be null");
    this.ttlNotFound = Objects.requireNonNull(ttlNotFound, "ttlNotFound must not be null");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
  }

  @Override
  public Optional<RedirectCacheEntry> findByShortCode(String shortCode) {
    return executeCacheSupplier(
        () -> {
          String value = redisTemplate.opsForValue().get(key(shortCode));

          if (value == null || value.isBlank()) {
            return Optional.empty();
          }

          return Optional.of(deserialize(value));
        });
  }

  @Override
  public boolean saveActiveIfAbsent(String shortCode, String longUrl) {
    return executeCacheSupplier(
        () -> {
          var entry = new RedirectCacheEntry(RedirectCacheStatus.ACTIVE, longUrl);
          return Boolean.TRUE.equals(
              redisTemplate.opsForValue().setIfAbsent(key(shortCode), serialize(entry), ttlActive));
        });
  }

  @Override
  public void saveActive(String shortCode, String longUrl) {
    executeCacheOperation(
        () -> {
          var entry = new RedirectCacheEntry(RedirectCacheStatus.ACTIVE, longUrl);
          redisTemplate.opsForValue().set(key(shortCode), serialize(entry), ttlActive);
        });
  }

  @Override
  public void saveDeleted(String shortCode) {
    executeCacheOperation(
        () -> {
          var entry = new RedirectCacheEntry(RedirectCacheStatus.DELETED, null);
          redisTemplate.opsForValue().set(key(shortCode), serialize(entry), ttlDeleted);
        });
  }

  @Override
  public boolean saveNotFoundIfAbsent(String shortCode) {
    return executeCacheSupplier(
        () -> {
          var entry = new RedirectCacheEntry(RedirectCacheStatus.NOT_FOUND, null);
          return Boolean.TRUE.equals(
              redisTemplate
                  .opsForValue()
                  .setIfAbsent(key(shortCode), serialize(entry), ttlNotFound));
        });
  }

  @Override
  public void evict(String shortCode) {
    executeCacheOperation(() -> redisTemplate.delete(key(shortCode)));
  }

  private String key(String shortCode) {
    return KEY_STRING + shortCode;
  }

  private String serialize(RedirectCacheEntry entry) {
    return objectMapper.writeValueAsString(entry);
  }

  private RedirectCacheEntry deserialize(String value) {
    return objectMapper.readValue(value, RedirectCacheEntry.class);
  }

  private void executeCacheOperation(CacheOperation operation) {
    try {
      operation.execute();
    } catch (RuntimeException exception) {
      throw new RedirectCacheException(exception);
    }
  }

  private <T> T executeCacheSupplier(CacheSupplier<T> supplier) {
    try {
      return supplier.get();
    } catch (RuntimeException exception) {
      throw new RedirectCacheException(exception);
    }
  }

  @FunctionalInterface
  private interface CacheOperation {
    void execute();
  }

  @FunctionalInterface
  private interface CacheSupplier<T> {
    T get();
  }
}
