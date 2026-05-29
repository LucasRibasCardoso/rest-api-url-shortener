package com.app.url_shortener.shared.database.idempotency.impl;

import com.app.url_shortener.config.BaseRedisSliceTest;
import com.app.url_shortener.shared.exception.internalservererror.IdempotencyCacheException;
import com.app.url_shortener.shared.idempotency.enums.IdempotencyStatus;
import com.app.url_shortener.shared.idempotency.impl.IdempotencyAdapter;
import com.app.url_shortener.shared.idempotency.valueobjects.CachedResponse;
import com.app.url_shortener.shared.idempotency.valueobjects.IdempotencyEntry;
import com.app.url_shortener.shared.idempotency.valueobjects.IdempotencyKey;
import com.app.url_shortener.shared.idempotency.valueobjects.RequestFingerprint;
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
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("redis-slice")
@Import({
        IdempotencyAdapter.class,
        RedisIdempotencyStoreObjectMapperTestConfig.class
})
@DisplayName("Slice Redis - Armazenamento de Idempotência")
class IdempotencyAdapterTest extends BaseRedisSliceTest {

  private static final String KEY_PREFIX = "idempotency:";
  private static final String KEY_PATTERN = KEY_PREFIX + "*";
  private static final RequestFingerprint FINGERPRINT = new RequestFingerprint(
          "user:1",
          "POST",
          "/api/v1/urls",
          "body-hash"
  );
  private static final String RAW_IDEMPOTENCY_KEY = "request-key";

  @Autowired
  private IdempotencyAdapter store;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @Autowired
  private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() {
    deleteIdempotencyKeys();
  }

  @AfterEach
  void tearDown() {
    deleteIdempotencyKeys();
  }

  @Nested
  @DisplayName("Estado em progresso")
  class SaveInProgressTests {

    @Test
    @DisplayName("Deve salvar estado em progresso quando chave não existir")
    void shouldSaveInProgressWhenKeyDoesNotExist() {
      // 1. Arrange
      var idempotencyKey = idempotencyKey("create-user-absent");
      var redisKey = redisKey(idempotencyKey);
      var ttl = Duration.ofMinutes(5);

      // 2. Act
      var result = store.saveInProgress(idempotencyKey, ttl);

      // 3. Assert
      assertThat(result).isTrue();

      var storedEntry = objectMapper.readValue(redisTemplate.opsForValue().get(redisKey), IdempotencyEntry.class);
      assertThat(storedEntry.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
      assertThat(storedEntry.fingerprint()).isEqualTo(FINGERPRINT);
      assertThat(storedEntry.response()).isNull();

      var ttlMillis = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
      assertThat(ttlMillis)
              .isNotNull()
              .isPositive()
              .isLessThanOrEqualTo(ttl.toMillis())
              .isGreaterThan(ttl.minusSeconds(5).toMillis());
    }

    @Test
    @DisplayName("Deve retornar falso quando chave já existir")
    void shouldReturnFalseWhenKeyAlreadyExists() {
      // 1. Arrange
      var idempotencyKey = idempotencyKey("create-user-existing");
      var firstResult = store.saveInProgress(idempotencyKey, Duration.ofMinutes(5));

      // 2. Act
      var secondResult = store.saveInProgress(idempotencyKey, Duration.ofMinutes(5));

      // 3. Assert
      assertThat(firstResult).isTrue();
      assertThat(secondResult).isFalse();

      var storedEntry = objectMapper.readValue(redisTemplate.opsForValue().get(redisKey(idempotencyKey)), IdempotencyEntry.class);
      assertThat(storedEntry.status()).isEqualTo(IdempotencyStatus.IN_PROGRESS);
    }
  }

  @Nested
  @DisplayName("Leitura de estado")
  class FindTests {

    @Test
    @DisplayName("Deve desserializar entrada em cache salva como JSON no Redis")
    void shouldDeserializeEntryFromRedisJson() {
      // 1. Arrange
      var idempotencyKey = idempotencyKey("completed-json");
      var entry = completedEntry();
      redisTemplate.opsForValue().set(redisKey(idempotencyKey), objectMapper.writeValueAsString(entry));

      // 2. Act
      var result = store.find(idempotencyKey);

      // 3. Assert
      assertThat(result).contains(entry);
    }

    @Test
    @DisplayName("Deve retornar Optional vazio quando chave não existir")
    void shouldReturnEmptyOptionalWhenKeyDoesNotExist() {
      // 1. Arrange
      var idempotencyKey = idempotencyKey("missing-state");

      // 2. Act
      var result = store.find(idempotencyKey);

      // 3. Assert
      assertThat(result).isEmpty();
      assertThat(redisTemplate.hasKey(redisKey(idempotencyKey))).isFalse();
    }

    @Test
    @DisplayName("Deve lançar IdempotencyCacheException quando JSON estiver inválido")
    void shouldThrowIdempotencyCacheExceptionWhenJsonIsInvalid() {
      // 1. Arrange
      var idempotencyKey = idempotencyKey("bad-json");
      redisTemplate.opsForValue().set(redisKey(idempotencyKey), "{invalid-json");

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> store.find(idempotencyKey))
              .isInstanceOf(IdempotencyCacheException.class)
              .hasMessage("Falha ao desserializar o cache de idempotency-key");
    }
  }

  @Nested
  @DisplayName("Estado concluído")
  class SaveCompletedTests {

    @Test
    @DisplayName("Deve serializar entrada concluída e salvar com TTL")
    void shouldSerializeCompletedEntryAndSaveWithTtl() {
      // 1. Arrange
      var idempotencyKey = idempotencyKey("completed-save");
      var redisKey = redisKey(idempotencyKey);
      var cachedResponse = cachedResponse(200, "{\"shortCode\":\"abc123\"}");
      var ttl = Duration.ofHours(1);

      // 2. Act
      store.saveCompleted(idempotencyKey, cachedResponse, ttl);

      // 3. Assert
      var storedJson = redisTemplate.opsForValue().get(redisKey);
      assertThat(storedJson).isNotBlank();

      var storedEntry = objectMapper.readValue(storedJson, IdempotencyEntry.class);
      assertThat(storedEntry.status()).isEqualTo(IdempotencyStatus.COMPLETED);
      assertThat(storedEntry.fingerprint()).isEqualTo(FINGERPRINT);
      assertThat(storedEntry.response()).isEqualTo(cachedResponse);

      var ttlMillis = redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS);
      assertThat(ttlMillis)
              .isNotNull()
              .isPositive()
              .isLessThanOrEqualTo(ttl.toMillis())
              .isGreaterThan(ttl.minusSeconds(5).toMillis());
    }
  }

  @Nested
  @DisplayName("Remoção")
  class DeleteTests {

    @Test
    @DisplayName("Deve remover chave do Redis")
    void shouldRemoveKeyFromRedis() {
      // 1. Arrange
      var idempotencyKey = idempotencyKey("delete-key");
      var redisKey = redisKey(idempotencyKey);
      store.saveInProgress(idempotencyKey, Duration.ofMinutes(5));

      // 2. Act
      store.delete(idempotencyKey);

      // 3. Assert
      assertThat(redisTemplate.hasKey(redisKey)).isFalse();
      assertThat(redisTemplate.getExpire(redisKey, TimeUnit.MILLISECONDS)).isEqualTo(-2L);
    }
  }

  private void deleteIdempotencyKeys() {
    Set<String> keys = redisTemplate.keys(KEY_PATTERN);
    if (keys != null && !keys.isEmpty()) {
      redisTemplate.delete(keys);
    }
  }

  private static IdempotencyEntry completedEntry() {
    return new IdempotencyEntry(
            IdempotencyStatus.COMPLETED,
            FINGERPRINT,
            cachedResponse(201, "{\"id\":\"123\"}"),
            Instant.parse("2026-05-29T12:00:00Z")
    );
  }

  private static CachedResponse cachedResponse(int status, String body) {
    return new CachedResponse(status, body);
  }

  private static IdempotencyKey idempotencyKey(String idempotencyKey) {
    return IdempotencyKey.generate(RAW_IDEMPOTENCY_KEY + "-" + idempotencyKey, FINGERPRINT);
  }

  private static String redisKey(IdempotencyKey idempotencyKey) {
    return KEY_PREFIX + idempotencyKey.value();
  }
}

@TestConfiguration
class RedisIdempotencyStoreObjectMapperTestConfig {

  @Bean
  ObjectMapper objectMapper() {
    return new ObjectMapper();
  }
}
