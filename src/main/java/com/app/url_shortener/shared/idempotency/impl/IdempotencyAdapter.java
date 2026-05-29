package com.app.url_shortener.shared.idempotency.impl;

import com.app.url_shortener.shared.exception.internalservererror.IdempotencyCacheException;
import com.app.url_shortener.shared.idempotency.port.IdempotencyPort;
import com.app.url_shortener.shared.idempotency.enums.IdempotencyStatus;
import com.app.url_shortener.shared.idempotency.valueobjects.CachedResponse;
import com.app.url_shortener.shared.idempotency.valueobjects.IdempotencyEntry;
import com.app.url_shortener.shared.idempotency.valueobjects.IdempotencyKey;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IdempotencyAdapter implements IdempotencyPort {

  private static final String KEY_PREFIX = "idempotency:";

  private final ObjectMapper objectMapper;
  private final StringRedisTemplate redisTemplate;

  @Override
  public boolean saveInProgress(IdempotencyKey idempotencyKey, Duration ttl) {
    var entry = new IdempotencyEntry(IdempotencyStatus.IN_PROGRESS, idempotencyKey.fingerprint(), null, Instant.now());

    return Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(redisKey(idempotencyKey), serialize(entry), ttl)
    );
  }

  @Override
  public Optional<IdempotencyEntry> find(IdempotencyKey idempotencyKey) {
    String state = redisTemplate.opsForValue().get(redisKey(idempotencyKey));

    if (state == null || state.isBlank()) {
      return Optional.empty();
    }

    try {
      return Optional.of(objectMapper.readValue(state, IdempotencyEntry.class));
    } catch (Exception e) {
      throw new IdempotencyCacheException("Falha ao desserializar o cache de idempotency-key");
    }
  }

  @Override
  public void saveCompleted(IdempotencyKey idempotencyKey, CachedResponse response, Duration ttl) {
    var entry = new IdempotencyEntry(IdempotencyStatus.COMPLETED, idempotencyKey.fingerprint(), response, Instant.now());
    redisTemplate.opsForValue().set(redisKey(idempotencyKey), serialize(entry), ttl);
  }

  @Override
  public void delete(IdempotencyKey idempotencyKey) {
    redisTemplate.delete(redisKey(idempotencyKey));
  }

  private String serialize(IdempotencyEntry entry) {
    try {
      return objectMapper.writeValueAsString(entry);
    } catch (Exception e) {
      throw new IdempotencyCacheException("Erro ao serializar o cache de idempotência para o Redis");
    }
  }

  private static String redisKey(IdempotencyKey idempotencyKey) {
    return KEY_PREFIX + idempotencyKey.value();
  }
}
