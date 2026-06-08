package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.EmailVerificationTokenStorePort;
import com.app.url_shortener.iam.domain.valueobject.EmailVerificationToken;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class EmailVerificationTokenStoreAdapter implements EmailVerificationTokenStorePort {

  private static final String KEY_PREFIX = "auth:email-verification:";
  private static final String VALUE_SEPARATOR = "|";
  private static final RedisScript<String> CONSUME_IF_CODE_MATCHES_SCRIPT =
      RedisScript.of(
          """
            local value = redis.call('GET', KEYS[1])
            if not value then
              return nil
            end

            local first_separator = string.find(value, '|', 1, true)
            if not first_separator then
              return nil
            end

            local second_separator = string.find(value, '|', first_separator + 1, true)
            if not second_separator then
              return nil
            end

            local stored_code = string.sub(value, first_separator + 1, second_separator - 1)
            if stored_code ~= ARGV[1] then
              return nil
            end

            redis.call('DEL', KEYS[1])
            return value
            """,
          String.class);

  private final StringRedisTemplate redisTemplate;

  @Override
  public void store(EmailVerificationToken token, Duration ttl) {
    String value =
        token.userId()
            + VALUE_SEPARATOR
            + token.code().value()
            + VALUE_SEPARATOR
            + token.expiresAt();

    redisTemplate.opsForValue().set(key(token.email()), value, ttl);
  }

  @Override
  public Optional<EmailVerificationToken> consumeByEmailAndCode(String email, VerificationCode code) {
    String value = redisTemplate.execute(CONSUME_IF_CODE_MATCHES_SCRIPT, List.of(key(email)), code.value());
    return deserialize(email, value);
  }

  private Optional<EmailVerificationToken> deserialize(String email, String value) {
    if (value == null) {
      return Optional.empty();
    }

    String[] parts = value.split("\\|");
    if (parts.length != 3) {
      return Optional.empty();
    }

    return Optional.of(
        EmailVerificationToken.create(
            UUID.fromString(parts[0]),
            email,
            VerificationCode.of(parts[1]),
            Instant.parse(parts[2])));
  }

  private String key(String email) {
    return KEY_PREFIX + email;
  }
}
