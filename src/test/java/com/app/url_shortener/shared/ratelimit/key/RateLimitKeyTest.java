package com.app.url_shortener.shared.ratelimit.key;

import com.app.url_shortener.shared.ratelimit.core.RateLimitPolicy;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Chave de Rate Limit")
class RateLimitKeyTest {

  @Nested
  @DisplayName("Chave por IP e Email")
  class IpAndEmailKeyTests {

    @Test
    @DisplayName("Deve criar chave com policy, IP e hash de email")
    void shouldCreateKeyWithPolicyIpAndEmailHash() {
      // 1. Arrange

      // 2. Act
      var key = RateLimitKey.createForIpAndEmail(RateLimitPolicy.AUTH_LOGIN, "203.0.113.10", "hashed-email");

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("auth-login:ip:203.0.113.10:email:hashed-email");
    }

    @Test
    @DisplayName("Deve rejeitar policy nula")
    void shouldRejectNullPolicy() {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> RateLimitKey.createForIpAndEmail(null, "203.0.113.10", "hashed-email"));

      // 3. Assert
      throwableAssert
          .isInstanceOf(NullPointerException.class)
          .hasMessage("policy must not be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("Deve rejeitar IP nulo, vazio ou em branco")
    void shouldRejectNullEmptyOrBlankClientIp(String clientIp) {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(
          () -> RateLimitKey.createForIpAndEmail(RateLimitPolicy.AUTH_LOGIN, clientIp, "hashed-email"));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("clientIp must not be blank");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("Deve rejeitar hash de email nulo, vazio ou em branco")
    void shouldRejectNullEmptyOrBlankEmailHash(String emailHash) {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(
          () -> RateLimitKey.createForIpAndEmail(RateLimitPolicy.AUTH_LOGIN, "203.0.113.10", emailHash));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("emailHash must not be blank");
    }
  }

  @Nested
  @DisplayName("Chave por Email")
  class EmailKeyTests {

    @Test
    @DisplayName("Deve criar chave com policy e hash de email")
    void shouldCreateKeyWithPolicyAndEmailHash() {
      // 1. Arrange

      // 2. Act
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_VERIFY_EMAIL, "hashed-email");

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("auth-verify-email:email:hashed-email");
    }

    @Test
    @DisplayName("Deve rejeitar policy nula")
    void shouldRejectNullPolicy() {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> RateLimitKey.createForEmail(null, "hashed-email"));

      // 3. Assert
      throwableAssert
          .isInstanceOf(NullPointerException.class)
          .hasMessage("policy must not be null");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\n"})
    @DisplayName("Deve rejeitar hash de email nulo, vazio ou em branco")
    void shouldRejectNullEmptyOrBlankEmailHash(String emailHash) {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(
          () -> RateLimitKey.createForEmail(RateLimitPolicy.AUTH_VERIFY_EMAIL, emailHash));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("emailHash must not be blank");
    }
  }

  @Nested
  @DisplayName("Chave por Usuário")
  class UserKeyTests {

    @Test
    @DisplayName("Deve criar chave com policy e usuário")
    void shouldCreateKeyWithPolicyAndUserId() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");

      // 2. Act
      var key = RateLimitKey.createForUserId(RateLimitPolicy.URL_SHORTEN_FREE, userId);

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("url-shorten-free:user:019a16f1-ae7f-7c9d-9e18-44773f1ad101");
    }

    @Test
    @DisplayName("Deve rejeitar policy nula")
    void shouldRejectNullPolicy() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> RateLimitKey.createForUserId(null, userId));

      // 3. Assert
      throwableAssert
          .isInstanceOf(NullPointerException.class)
          .hasMessage("policy must not be null");
    }

    @Test
    @DisplayName("Deve rejeitar usuário nulo")
    void shouldRejectNullUserId() {
      // 1. Arrange

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> RateLimitKey.createForUserId(RateLimitPolicy.URL_SHORTEN_FREE, null));

      // 3. Assert
      throwableAssert
          .isInstanceOf(NullPointerException.class)
          .hasMessage("userId must not be null");
    }
  }

  @Nested
  @DisplayName("Comportamento de Value Object")
  class ValueObjectTests {

    @Test
    @DisplayName("Deve considerar iguais chaves com mesmo valor")
    void shouldConsiderKeysWithSameValueEqual() {
      // 1. Arrange
      var firstKey = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_LOGIN, "hashed-email");
      var secondKey = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_LOGIN, "hashed-email");

      // 2. Act & 3. Assert
      assertThat(firstKey)
          .isEqualTo(secondKey)
          .hasSameHashCodeAs(secondKey);
    }

    @Test
    @DisplayName("Deve considerar diferentes chaves com valores diferentes")
    void shouldConsiderKeysWithDifferentValuesDifferent() {
      // 1. Arrange
      var firstKey = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_LOGIN, "first-hash");
      var secondKey = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_LOGIN, "second-hash");

      // 2. Act & 3. Assert
      assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    @DisplayName("Deve redigir valor sensível no toString")
    void shouldRedactSensitiveValueInToString() {
      // 1. Arrange
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_LOGIN, "hashed-email");

      // 2. Act
      var result = key.toString();

      // 3. Assert
      assertThat(result)
          .isEqualTo("RateLimitKey{value='[REDACTED]'}")
          .doesNotContain("hashed-email")
          .doesNotContain(key.getValue());
    }
  }
}
