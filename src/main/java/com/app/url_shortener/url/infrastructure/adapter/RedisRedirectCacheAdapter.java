package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.port.output.RedirectCachePort;
import com.app.url_shortener.url.application.result.RedirectCacheStatus;
import com.app.url_shortener.url.application.result.UrlRedirectCacheEntry;
import com.app.url_shortener.url.domain.exception.RedirectCacheException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
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
  public Optional<UrlRedirectCacheEntry> findByShortCode(String shortCode) {
    String value = redisTemplate.opsForValue().get(key(shortCode));

    if (value == null || value.isBlank()) {
      return Optional.empty();
    }

    return Optional.of(deserialize(value));
  }

  @Override
  public boolean saveActiveIfAbsent(String shortCode, String longUrl) {
    var entry = new UrlRedirectCacheEntry(RedirectCacheStatus.ACTIVE, longUrl);
    return Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(key(shortCode), serialize(entry), ttlActive));
  }

  @Override
  public void saveActive(String shortCode, String longUrl) {
    var entry = new UrlRedirectCacheEntry(RedirectCacheStatus.ACTIVE, longUrl);
    redisTemplate.opsForValue().set(key(shortCode), serialize(entry), ttlActive);
  }

  @Override
  public void saveDeleted(String shortCode) {
    var entry = new UrlRedirectCacheEntry(RedirectCacheStatus.DELETED, null);
    redisTemplate.opsForValue().set(key(shortCode), serialize(entry), ttlDeleted);
  }

  @Override
  public boolean saveNotFoundIfAbsent(String shortCode) {
    var entry = new UrlRedirectCacheEntry(RedirectCacheStatus.NOT_FOUND, null);
    return Boolean.TRUE.equals(
        redisTemplate.opsForValue().setIfAbsent(key(shortCode), serialize(entry), ttlNotFound));
  }

  @Override
  public void evict(String shortCode) {
    redisTemplate.delete(key(shortCode));
  }

  private String key(String shortCode) {
    return KEY_STRING + shortCode;
  }

  private String serialize(UrlRedirectCacheEntry entry) {
    try {
      return objectMapper.writeValueAsString(entry);
    } catch (JacksonException e) {
      throw new RedirectCacheException(e);
    }
  }

  private UrlRedirectCacheEntry deserialize(String value) {
    try {
      return objectMapper.readValue(value, UrlRedirectCacheEntry.class);
    } catch (JacksonException e) {
      throw new RedirectCacheException(e);
    }
  }
}
