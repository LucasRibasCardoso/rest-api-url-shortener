package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.domain.exception.auth.VerificationCodeProtectionException;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Proteção do Código de Verificação")
class VerificationCodeProtectorAdapterTest {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final byte[] HMAC_SECRET =
      "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);

  @Mock private TextEncryptor textEncryptor;

  @Nested
  @DisplayName("Hash")
  class HashTests {

    @Test
    @DisplayName("Deve gerar hash HMAC-SHA256 em Base64")
    void shouldGenerateBase64HmacSha256Hash() throws Exception {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var adapter = adapter();

      // 2. Act
      var result = adapter.hash(code);

      // 3. Assert
      assertThat(result).isEqualTo(expectedHash(code));
      verifyNoInteractions(textEncryptor);
    }

    @Test
    @DisplayName("Deve rejeitar código nulo ao gerar hash")
    void shouldRejectNullCodeWhenHashing() {
      // 1. Arrange
      var adapter = adapter();

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.hash(null));

      // 3. Assert
      throwableAssert
          .isInstanceOf(VerificationCodeProtectionException.class)
          .extracting("errorCode")
          .isEqualTo(IamErrorCode.AUTH_VERIFICATION_CODE_INVALID);
      verifyNoInteractions(textEncryptor);
    }
  }

  @Nested
  @DisplayName("Comparação")
  class MatchesTests {

    @Test
    @DisplayName("Deve retornar verdadeiro quando código e hash coincidirem")
    void shouldReturnTrueWhenCodeMatchesHash() throws Exception {
      // 1. Arrange
      var code = VerificationCode.of("123456");
      var adapter = adapter();
      var hash = expectedHash(code);

      // 2. Act
      var result = adapter.matches(code, hash);

      // 3. Assert
      assertThat(result).isTrue();
      verifyNoInteractions(textEncryptor);
    }

    @Test
    @DisplayName("Deve retornar falso quando código e hash divergirem")
    void shouldReturnFalseWhenCodeDoesNotMatchHash() throws Exception {
      // 1. Arrange
      var adapter = adapter();
      var hash = expectedHash(VerificationCode.of("123456"));

      // 2. Act
      var result = adapter.matches(VerificationCode.of("654321"), hash);

      // 3. Assert
      assertThat(result).isFalse();
      verifyNoInteractions(textEncryptor);
    }

    @Test
    @DisplayName("Deve retornar falso para hash vazio")
    void shouldReturnFalseWhenHashIsBlank() {
      // 1. Arrange
      var adapter = adapter();

      // 2. Act
      var result = adapter.matches(VerificationCode.of("123456"), " ");

      // 3. Assert
      assertThat(result).isFalse();
      verifyNoInteractions(textEncryptor);
    }
  }

  @Nested
  @DisplayName("Criptografia")
  class EncryptTests {

    @Test
    @DisplayName("Deve criptografar valor do código")
    void shouldEncryptVerificationCodeValue() {
      // 1. Arrange
      var adapter = adapter();
      var code = VerificationCode.of("123456");
      given(textEncryptor.encrypt("123456")).willReturn("encrypted-code");

      // 2. Act
      var result = adapter.encrypt(code);

      // 3. Assert
      assertThat(result).isEqualTo("encrypted-code");
      verify(textEncryptor).encrypt("123456");
      verifyNoMoreInteractions(textEncryptor);
    }

    @Test
    @DisplayName("Deve traduzir falha de criptografia")
    void shouldTranslateEncryptionFailure() {
      // 1. Arrange
      var adapter = adapter();
      given(textEncryptor.encrypt("123456")).willThrow(new IllegalStateException("encrypt failure"));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.encrypt(VerificationCode.of("123456")));

      // 3. Assert
      throwableAssert
          .isInstanceOf(VerificationCodeProtectionException.class)
          .hasCauseInstanceOf(IllegalStateException.class)
          .extracting("errorCode")
          .isEqualTo(IamErrorCode.AUTH_VERIFICATION_CODE_ENCRYPT_FAILED);
      verify(textEncryptor).encrypt("123456");
      verifyNoMoreInteractions(textEncryptor);
    }
  }

  @Nested
  @DisplayName("Descriptografia")
  class DecryptTests {

    @Test
    @DisplayName("Deve descriptografar código válido")
    void shouldDecryptValidVerificationCode() {
      // 1. Arrange
      var adapter = adapter();
      given(textEncryptor.decrypt("encrypted-code")).willReturn("123456");

      // 2. Act
      var result = adapter.decrypt("encrypted-code");

      // 3. Assert
      assertThat(result).isEqualTo(VerificationCode.of("123456"));
      verify(textEncryptor).decrypt("encrypted-code");
      verifyNoMoreInteractions(textEncryptor);
    }

    @Test
    @DisplayName("Deve rejeitar texto criptografado em branco")
    void shouldRejectBlankEncryptedCode() {
      // 1. Arrange
      var adapter = adapter();

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.decrypt(" "));

      // 3. Assert
      throwableAssert
          .isInstanceOf(VerificationCodeProtectionException.class)
          .extracting("errorCode")
          .isEqualTo(IamErrorCode.AUTH_ENCRYPTED_VERIFICATION_CODE_INVALID);
      verifyNoInteractions(textEncryptor);
    }

    @Test
    @DisplayName("Deve traduzir falha de descriptografia")
    void shouldTranslateDecryptionFailure() {
      // 1. Arrange
      var adapter = adapter();
      given(textEncryptor.decrypt("encrypted-code")).willThrow(new IllegalStateException("decrypt failure"));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> adapter.decrypt("encrypted-code"));

      // 3. Assert
      throwableAssert
          .isInstanceOf(VerificationCodeProtectionException.class)
          .hasCauseInstanceOf(IllegalStateException.class)
          .extracting("errorCode")
          .isEqualTo(IamErrorCode.AUTH_VERIFICATION_CODE_DECRYPT_FAILED);
      verify(textEncryptor).decrypt("encrypted-code");
      verifyNoMoreInteractions(textEncryptor);
    }
  }

  private VerificationCodeProtectorAdapter adapter() {
    return new VerificationCodeProtectorAdapter(new SecretKeySpec(HMAC_SECRET, HMAC_ALGORITHM), textEncryptor);
  }

  private String expectedHash(VerificationCode code) throws Exception {
    Mac mac = Mac.getInstance(HMAC_ALGORITHM);
    mac.init(new SecretKeySpec(HMAC_SECRET, HMAC_ALGORITHM));
    byte[] digest = mac.doFinal(code.value().getBytes(StandardCharsets.UTF_8));
    return Base64.getEncoder().encodeToString(digest);
  }
}
