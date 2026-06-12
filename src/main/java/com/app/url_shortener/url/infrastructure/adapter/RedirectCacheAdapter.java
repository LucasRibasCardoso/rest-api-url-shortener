package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.port.output.model.RedirectCacheStatus;
import com.app.url_shortener.url.application.port.output.model.RedirectCacheEntry;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import com.app.url_shortener.url.infrastructure.config.RedirectCacheProperties;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
public class RedirectCacheAdapter implements RedirectCachePort {

  private static final String KEY_STRING = "url:redirect:";

  private final Duration ttlActive;
  private final Duration ttlDeleted;
  private final Duration ttlNotFound;
  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redisTemplate;

  public RedirectCacheAdapter(
      RedirectCacheProperties properties,
      ObjectMapper objectMapper,
      StringRedisTemplate redisTemplate) {
    this.ttlActive = properties.ttl().active();
    this.ttlDeleted = properties.ttl().deleted();
    this.ttlNotFound = properties.ttl().notFound();
    this.objectMapper = objectMapper;
    this.redisTemplate = redisTemplate;
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
  public boolean saveActiveIfAbsent(String shortCode, String originalUrl) {
    return executeCacheSupplier(
        () -> {
          var entry = new RedirectCacheEntry(RedirectCacheStatus.ACTIVE, originalUrl);
          return Boolean.TRUE.equals(
              redisTemplate.opsForValue().setIfAbsent(key(shortCode), serialize(entry), ttlActive));
        });
  }

  @Override
  public void saveActive(String shortCode, String originalUrl) {
    executeCacheOperation(
        () -> {
          var entry = new RedirectCacheEntry(RedirectCacheStatus.ACTIVE, originalUrl);
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
