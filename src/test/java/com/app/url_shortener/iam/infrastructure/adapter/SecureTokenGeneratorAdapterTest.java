package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.util.Base64;
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
@DisplayName("Testes de Unidade - Adaptador de Geração de Token Seguro")
class SecureTokenGeneratorAdapterTest {

  @Nested
  @DisplayName("Geração de Token Aleatório")
  class GenerateRandomTokenTests {

    @Test
    @DisplayName("Deve gerar token Base64 URL-safe sem padding a partir de 32 bytes aleatórios")
    void shouldGenerateUrlSafeBase64TokenWithoutPaddingFromThirtyTwoRandomBytes() {
      // 1. Arrange
      var randomBytes = sequentialBytes();
      var secureRandom = new FixedSecureRandom(randomBytes);
      var adapter = new SecureTokenGeneratorAdapter(secureRandom);
      var expectedToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

      // 2. Act
      var token = adapter.generateRandomToken();

      // 3. Assert
      assertThat(token).isEqualTo(expectedToken);
      assertThat(token).hasSize(43);
      assertThat(token).matches("^[A-Za-z0-9_-]+$");
      assertThat(secureRandom.requestedLength()).isEqualTo(32);
      assertThat(secureRandom.invocations()).isEqualTo(1);
    }
  }

  @Nested
  @DisplayName("Hash de Token")
  class HashTokenTests {

    @Test
    @DisplayName("Deve gerar hash SHA-256 hexadecimal para token válido")
    void shouldGenerateSha256HexHashForValidToken() {
      // 1. Arrange
      var adapter = new SecureTokenGeneratorAdapter(new FixedSecureRandom(sequentialBytes()));
      var rawToken = "raw-refresh-token";

      // 2. Act
      var hash = adapter.hashToken(rawToken);

      // 3. Assert
      assertThat(hash)
          .isEqualTo("0881b36898a91d864edaf39d2b2bd5801d5f873e3142a9ec5b3b574c4f6b51e5")
          .hasSize(64)
          .matches("^[0-9a-f]{64}$");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "   ", "\t", "\n"})
    @DisplayName("Deve rejeitar hash quando token for nulo, vazio ou em branco")
    void shouldRejectHashWhenTokenIsNullEmptyOrBlank(String rawToken) {
      // 1. Arrange
      var adapter = new SecureTokenGeneratorAdapter(new FixedSecureRandom(sequentialBytes()));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.hashToken(rawToken));

      // 3. Assert
      throwableAssert
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Não é possível gerar hash de um token vazio ou nulo");
    }
  }

  private byte[] sequentialBytes() {
    byte[] bytes = new byte[32];
    for (int index = 0; index < bytes.length; index++) {
      bytes[index] = (byte) index;
    }
    return bytes;
  }

  private static class FixedSecureRandom extends SecureRandom {

    private final byte[] bytes;
    private int requestedLength;
    private int invocations;

    private FixedSecureRandom(byte[] bytes) {
      this.bytes = bytes.clone();
    }

    @Override
    public void nextBytes(byte[] targetBytes) {
      requestedLength = targetBytes.length;
      invocations++;
      System.arraycopy(bytes, 0, targetBytes, 0, targetBytes.length);
    }

    private int requestedLength() {
      return requestedLength;
    }

    private int invocations() {
      return invocations;
    }
  }
}
