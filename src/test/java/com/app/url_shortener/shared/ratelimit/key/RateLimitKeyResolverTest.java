package com.app.url_shortener.shared.ratelimit.key;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Resolvedor de Chaves de Rate Limit")
class RateLimitKeyResolverTest {

  @Mock private EmailRateLimitKeyHasher emailRateLimitKeyHasher;

  @InjectMocks private RateLimitKeyResolver resolver;

  @Nested
  @DisplayName("Chaves por Email")
  class EmailKeyTests {

    @Test
    @DisplayName("Deve criar chave de cadastro somente com hash de email")
    void shouldCreateRegisterKeyOnlyWithEmailHash() {
      // 1. Arrange
      var email = "user@example.com";
      given(emailRateLimitKeyHasher.hash(email)).willReturn("hashed-email");

      // 2. Act
      var key = resolver.registerByEmail(email);

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("auth-register-email:email:hashed-email");
      verify(emailRateLimitKeyHasher).hash(email);
      verifyNoMoreInteractions(emailRateLimitKeyHasher);
    }

    @Test
    @DisplayName("Deve criar chave de login somente com hash de email")
    void shouldCreateLoginKeyOnlyWithEmailHash() {
      // 1. Arrange
      var email = "user@example.com";
      given(emailRateLimitKeyHasher.hash(email)).willReturn("hashed-email");

      // 2. Act
      var key = resolver.loginByEmail(email);

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("auth-login:email:hashed-email");
      verify(emailRateLimitKeyHasher).hash(email);
      verifyNoMoreInteractions(emailRateLimitKeyHasher);
    }

    @Test
    @DisplayName("Deve criar chave de verificação de email somente com hash de email")
    void shouldCreateVerifyEmailKeyOnlyWithEmailHash() {
      // 1. Arrange
      var email = "user@example.com";
      given(emailRateLimitKeyHasher.hash(email)).willReturn("hashed-email");

      // 2. Act
      var key = resolver.verifyEmailByEmail(email);

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("auth-verify-email:email:hashed-email");
      verify(emailRateLimitKeyHasher).hash(email);
      verifyNoMoreInteractions(emailRateLimitKeyHasher);
    }

    @Test
    @DisplayName("Deve criar chave de reenvio de verificação somente com hash de email")
    void shouldCreateResendVerificationKeyOnlyWithEmailHash() {
      // 1. Arrange
      var email = "user@example.com";
      given(emailRateLimitKeyHasher.hash(email)).willReturn("hashed-email");

      // 2. Act
      var key = resolver.resendVerificationByEmail(email);

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("auth-resend-verification:email:hashed-email");
      verify(emailRateLimitKeyHasher).hash(email);
      verifyNoMoreInteractions(emailRateLimitKeyHasher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rejeitar email em branco para chave de login")
    void shouldRejectBlankEmailForLoginKey(String email) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.loginByEmail(email))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("email must not be blank");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rejeitar email em branco para chave de cadastro")
    void shouldRejectBlankEmailForRegisterKey(String email) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.registerByEmail(email))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("email must not be blank");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rejeitar email em branco para chave de verificação")
    void shouldRejectBlankEmailForVerifyEmailKey(String email) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.verifyEmailByEmail(email))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("email must not be blank");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rejeitar email em branco para chave de reenvio de verificação")
    void shouldRejectBlankEmailForResendVerificationKey(String email) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.resendVerificationByEmail(email))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("email must not be blank");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }
  }

  @Nested
  @DisplayName("Chaves Compostas")
  class CompositeKeyTests {

    @Test
    @DisplayName("Deve criar chave de cadastro somente com IP")
    void shouldCreateRegisterKeyOnlyWithIp() {
      // 1. Arrange
      var clientIp = "203.0.113.10";

      // 2. Act
      var key = resolver.registerByIp(clientIp);

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("auth-register-ip:ip:203.0.113.10");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rejeitar IP em branco para chave de cadastro")
    void shouldRejectBlankClientIpForRegisterKey(String clientIp) {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.registerByIp(clientIp))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("clientIp must not be blank");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @Test
    @DisplayName("Deve criar chave de login com IP e hash de email")
    void shouldCreateLoginKeyWithClientIpAndEmailHash() {
      // 1. Arrange
      var email = "user@example.com";
      var clientIp = "203.0.113.10";
      given(emailRateLimitKeyHasher.hash(email)).willReturn("hashed-email");

      // 2. Act
      var key = resolver.loginByIpAndEmail(clientIp, email);

      // 3. Assert
      assertThat(key.getValue()).isEqualTo("auth-login:ip:203.0.113.10:email:hashed-email");
      verify(emailRateLimitKeyHasher).hash(email);
      verifyNoMoreInteractions(emailRateLimitKeyHasher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rejeitar IP em branco para chave de login composta")
    void shouldRejectBlankClientIpForCompositeLoginKey(String clientIp) {
      // 1. Arrange
      var email = "user@example.com";

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.loginByIpAndEmail(clientIp, email))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("clientIp must not be blank");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rejeitar email em branco para chave de login composta")
    void shouldRejectBlankEmailForCompositeLoginKey(String email) {
      // 1. Arrange
      var clientIp = "203.0.113.10";

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.loginByIpAndEmail(clientIp, email))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("email must not be blank");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }
  }

  @Nested
  @DisplayName("Chaves por Usuário")
  class UserKeyTests {

    @Test
    @DisplayName("Deve criar chave de encurtamento com usuário e política")
    void shouldCreateShortenKeyWithUserAndPolicy() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");

      // 2. Act
      var key = resolver.shortenByUserIdAndPlan(userId, RateLimitPolicy.URL_SHORTEN_FREE);

      // 3. Assert
      assertThat(key.getValue())
          .isEqualTo("url-shorten-free:user:019a16f1-ae7f-7c9d-9e18-44773f1ad101");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @Test
    @DisplayName("Deve criar chave de encurtamento para plano premium")
    void shouldCreateShortenKeyForPremiumPlan() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");

      // 2. Act
      var key = resolver.shortenByUserIdAndPlan(userId, RateLimitPolicy.URL_SHORTEN_PREMIUM);

      // 3. Assert
      assertThat(key.getValue())
          .isEqualTo("url-shorten-premium:user:019a16f1-ae7f-7c9d-9e18-44773f1ad101");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @Test
    @DisplayName("Deve rejeitar política que não seja de encurtamento")
    void shouldRejectNonShortenPolicy() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.shortenByUserIdAndPlan(userId, RateLimitPolicy.AUTH_LOGIN))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Invalid shorten rate limit policy for user-based key: AUTH_LOGIN");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @Test
    @DisplayName("Deve rejeitar usuário nulo para chave de encurtamento")
    void shouldRejectNullUserIdForShortenKey() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () -> resolver.shortenByUserIdAndPlan(null, RateLimitPolicy.URL_SHORTEN_FREE))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("userId must not be null");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }

    @Test
    @DisplayName("Deve rejeitar política nula para chave de encurtamento")
    void shouldRejectNullPolicyForShortenKey() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> resolver.shortenByUserIdAndPlan(userId, null))
          .isInstanceOf(NullPointerException.class)
          .hasMessage("policy must not be null");
      verifyNoInteractions(emailRateLimitKeyHasher);
    }
  }
}
