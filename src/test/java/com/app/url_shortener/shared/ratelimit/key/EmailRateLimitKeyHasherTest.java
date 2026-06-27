package com.app.url_shortener.shared.ratelimit.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.app.url_shortener.shared.ratelimit.config.RateLimitProperties;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Hasher de Email para Rate Limit")
class EmailRateLimitKeyHasherTest {

  private static final String EMAIL_HASH_SECRET = "DtyLwE6d9sMslJ4WKXxZmMtYqwXRMJKuR1KlOiWBQVA=";

  @Nested
  @DisplayName("Hash de Email")
  class HashTests {

    @Test
    @DisplayName("Deve gerar hash SHA-256 hexadecimal para email normalizado")
    void shouldGenerateSha256HexHashForNormalizedEmail() {
      // 1. Arrange
      var hasher = new EmailRateLimitKeyHasher(rateLimitProperties());
      var email = " USER@EXAMPLE.COM ";

      // 2. Act
      var hash = hasher.hash(email);

      // 3. Assert
      assertThat(hash)
          .isEqualTo("a279e67a2f86aafe1691a2bea2bd1577132d185a9340a20655d2f19dbd87c13b")
          .hasSize(64)
          .matches("^[0-9a-f]{64}$");
    }

    @Test
    @DisplayName("Deve gerar o mesmo hash para emails com maiúsculas e espaços")
    void shouldGenerateSameHashForEmailsWithUppercaseAndSpaces() {
      // 1. Arrange
      var hasher = new EmailRateLimitKeyHasher(rateLimitProperties());

      // 2. Act
      var normalizedHash = hasher.hash("user@example.com");
      var formattedHash = hasher.hash("  USER@example.COM  ");

      // 3. Assert
      assertThat(formattedHash).isEqualTo(normalizedHash);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar hash quando email for nulo, vazio ou em branco")
    void shouldRejectHashWhenEmailIsNullEmptyOrBlank(String email) {
      // 1. Arrange
      var hasher = new EmailRateLimitKeyHasher(rateLimitProperties());

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> hasher.hash(email));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Email must not be null or blank");
    }
  }

  @Nested
  @DisplayName("Segredo de Hash")
  class SecretTests {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar segredo nulo, vazio ou em branco")
    void shouldRejectNullEmptyOrBlankSecret(String secret) {
      // 1. Arrange
      var properties = rateLimitProperties(secret);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> new EmailRateLimitKeyHasher(properties));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Rate limit email hash secret must not be blank");
    }

    @Test
    @DisplayName("Deve rejeitar segredo que não esteja em Base64")
    void shouldRejectSecretThatIsNotBase64Encoded() {
      // 1. Arrange
      var properties = rateLimitProperties("not-base64!");

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> new EmailRateLimitKeyHasher(properties));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Rate limit email hash secret must be Base64 encoded")
          .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Deve rejeitar segredo decodificado com menos de 32 bytes")
    void shouldRejectDecodedSecretWithLessThan32Bytes() {
      // 1. Arrange
      var properties = rateLimitProperties("c2hvcnQ=");

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> new EmailRateLimitKeyHasher(properties));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalStateException.class)
          .hasMessage("Rate limit email hash secret must decode to at least 32 bytes");
    }
  }

  private RateLimitProperties rateLimitProperties() {
    return rateLimitProperties(EMAIL_HASH_SECRET);
  }

  private RateLimitProperties rateLimitProperties(String emailHashSecret) {
    return new RateLimitProperties(true, "rate-limit", emailHashSecret, Map.of());
  }
}
