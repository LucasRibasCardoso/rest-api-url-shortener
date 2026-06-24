package com.app.url_shortener.shared.ratelimit.impl;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.shared.ratelimit.config.RateLimitPolicyProperties;
import com.app.url_shortener.shared.ratelimit.config.RateLimitProperties;
import com.app.url_shortener.shared.ratelimit.exception.RateLimitInfrastructureException;
import com.app.url_shortener.shared.ratelimit.exception.TooManyRequestsException;
import com.app.url_shortener.shared.ratelimit.core.RateLimitDecision;
import com.app.url_shortener.shared.ratelimit.core.RateLimitPolicy;
import com.app.url_shortener.shared.ratelimit.core.RateLimiterPort;
import com.app.url_shortener.shared.ratelimit.key.RateLimitKey;
import com.app.url_shortener.shared.ratelimit.key.RateLimitKeyResolver;
import java.time.Duration;
import java.util.UUID;

import com.app.url_shortener.shared.ratelimit.service.RateLimitServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Serviço de Rate Limit")
class RateLimitServiceImplTest {

  @Mock
  private RateLimiterPort rateLimiterPort;

  @Mock
  private RateLimitProperties properties;

  @Mock
  private RateLimitPolicyProperties policyProperties;

  @Mock
  private RateLimitKeyResolver keyResolver;

  @InjectMocks
  private RateLimitServiceImpl service;

  @Nested
  @DisplayName("Cadastro")
  class RegisterTests {

    @Test
    @DisplayName("Deve consultar separadamente as chaves de email e IP")
    void shouldCheckRegisterUsingSeparateEmailAndIpKeys() {
      // 1. Arrange
      var email = "user@example.com";
      var clientIp = "203.0.113.10";
      var emailKey =
          RateLimitKey.createForEmail(RateLimitPolicy.AUTH_REGISTER_EMAIL, "hashed-email");
      var ipKey = RateLimitKey.createForIp(RateLimitPolicy.AUTH_REGISTER_IP, clientIp);

      givenPolicyEnabled(RateLimitPolicy.AUTH_REGISTER_EMAIL);
      givenPolicyEnabled(RateLimitPolicy.AUTH_REGISTER_IP);
      given(keyResolver.registerByEmail(email)).willReturn(emailKey);
      given(keyResolver.registerByIp(clientIp)).willReturn(ipKey);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_REGISTER_EMAIL, emailKey))
          .willReturn(RateLimitDecision.allowed(2));
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_REGISTER_IP, ipKey))
          .willReturn(RateLimitDecision.allowed(19));

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkRegister(clientIp, email)).doesNotThrowAnyException();
      verify(keyResolver).registerByEmail(email);
      verify(keyResolver).registerByIp(clientIp);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_REGISTER_EMAIL, emailKey);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_REGISTER_IP, ipKey);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }

    @Test
    @DisplayName("Deve interromper antes da chave de IP quando o limite por email for excedido")
    void shouldStopBeforeIpCheckWhenEmailLimitIsExceeded() {
      // 1. Arrange
      var email = "user@example.com";
      var clientIp = "203.0.113.10";
      var emailKey =
          RateLimitKey.createForEmail(RateLimitPolicy.AUTH_REGISTER_EMAIL, "hashed-email");

      givenPolicyEnabled(RateLimitPolicy.AUTH_REGISTER_EMAIL);
      given(keyResolver.registerByEmail(email)).willReturn(emailKey);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_REGISTER_EMAIL, emailKey))
          .willReturn(RateLimitDecision.denied(0, Duration.ofMinutes(15).toNanos()));

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> service.checkRegister(clientIp, email));

      // 3. Assert
      throwableAssert.isInstanceOf(TooManyRequestsException.class);
      verify(keyResolver).registerByEmail(email);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_REGISTER_EMAIL, emailKey);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }

    @Test
    @DisplayName("Deve bloquear quando o limite por IP for excedido após permitir o email")
    void shouldBlockWhenIpLimitIsExceededAfterEmailIsAllowed() {
      // 1. Arrange
      var email = "user@example.com";
      var clientIp = "203.0.113.10";
      var emailKey =
          RateLimitKey.createForEmail(RateLimitPolicy.AUTH_REGISTER_EMAIL, "hashed-email");
      var ipKey = RateLimitKey.createForIp(RateLimitPolicy.AUTH_REGISTER_IP, clientIp);

      givenPolicyEnabled(RateLimitPolicy.AUTH_REGISTER_EMAIL);
      givenPolicyEnabled(RateLimitPolicy.AUTH_REGISTER_IP);
      given(keyResolver.registerByEmail(email)).willReturn(emailKey);
      given(keyResolver.registerByIp(clientIp)).willReturn(ipKey);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_REGISTER_EMAIL, emailKey))
          .willReturn(RateLimitDecision.allowed(2));
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_REGISTER_IP, ipKey))
          .willReturn(RateLimitDecision.denied(0, Duration.ofMinutes(15).toNanos()));

      // 2. Act
      var throwableAssert =
          assertThatThrownBy(() -> service.checkRegister(clientIp, email));

      // 3. Assert
      throwableAssert.isInstanceOf(TooManyRequestsException.class);
      verify(keyResolver).registerByEmail(email);
      verify(keyResolver).registerByIp(clientIp);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_REGISTER_EMAIL, emailKey);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_REGISTER_IP, ipKey);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }
  }

  @Nested
  @DisplayName("Login")
  class LoginTests {

    @Test
    @DisplayName("Deve consultar as chaves de email e IP com email")
    void shouldCheckLoginUsingEmailAndIpWithEmailKeys() {
      // 1. Arrange
      var email = "user@example.com";
      var clientIp = "203.0.113.10";
      var emailKey = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_LOGIN, "hashed-email");
      var ipAndEmailKey = RateLimitKey.createForIpAndEmail(RateLimitPolicy.AUTH_LOGIN, clientIp, "hashed-email");

      givenPolicyEnabled(RateLimitPolicy.AUTH_LOGIN);
      given(keyResolver.loginByEmail(email)).willReturn(emailKey);
      given(keyResolver.loginByIpAndEmail(clientIp, email)).willReturn(ipAndEmailKey);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_LOGIN, emailKey)).willReturn(RateLimitDecision.allowed(9));
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_LOGIN, ipAndEmailKey)).willReturn(RateLimitDecision.allowed(8));

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkLogin(clientIp, email)).doesNotThrowAnyException();
      verify(keyResolver).loginByEmail(email);
      verify(keyResolver).loginByIpAndEmail(clientIp, email);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_LOGIN, emailKey);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_LOGIN, ipAndEmailKey);
      verify(rateLimiterPort, times(2)).consume(eq(RateLimitPolicy.AUTH_LOGIN), any(RateLimitKey.class));
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }
  }

  @Nested
  @DisplayName("Encurtamento")
  class ShortenTests {

    @Test
    @DisplayName("Deve usar política de encurtamento FREE")
    void shouldUseFreeShortenPolicy() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");
      var key = RateLimitKey.createForUserId(RateLimitPolicy.URL_SHORTEN_FREE, userId);

      givenPolicyEnabled(RateLimitPolicy.URL_SHORTEN_FREE);
      given(keyResolver.shortenByUserIdAndPlan(userId, RateLimitPolicy.URL_SHORTEN_FREE)).willReturn(key);
      given(rateLimiterPort.consume(RateLimitPolicy.URL_SHORTEN_FREE, key)).willReturn(RateLimitDecision.allowed(9));

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkShorten(userId, PlanType.FREE)).doesNotThrowAnyException();
      verify(keyResolver).shortenByUserIdAndPlan(userId, RateLimitPolicy.URL_SHORTEN_FREE);
      verify(rateLimiterPort).consume(RateLimitPolicy.URL_SHORTEN_FREE, key);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }

    @Test
    @DisplayName("Deve usar política de encurtamento PREMIUM")
    void shouldUsePremiumShortenPolicy() {
      // 1. Arrange
      var userId = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ad101");
      var key = RateLimitKey.createForUserId(RateLimitPolicy.URL_SHORTEN_PREMIUM, userId);

      givenPolicyEnabled(RateLimitPolicy.URL_SHORTEN_PREMIUM);
      given(keyResolver.shortenByUserIdAndPlan(userId, RateLimitPolicy.URL_SHORTEN_PREMIUM)).willReturn(key);
      given(rateLimiterPort.consume(RateLimitPolicy.URL_SHORTEN_PREMIUM, key)).willReturn(RateLimitDecision.allowed(99));

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkShorten(userId, PlanType.PREMIUM)).doesNotThrowAnyException();
      verify(keyResolver).shortenByUserIdAndPlan(userId, RateLimitPolicy.URL_SHORTEN_PREMIUM);
      verify(rateLimiterPort).consume(RateLimitPolicy.URL_SHORTEN_PREMIUM, key);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }
  }

  @Nested
  @DisplayName("Verificação de Email")
  class EmailVerificationTests {

    @Test
    @DisplayName("Deve delegar reenvio de verificação corretamente")
    void shouldDelegateResendVerification() {
      // 1. Arrange
      var email = "user@example.com";
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_RESEND_VERIFICATION, "hashed-email");

      givenPolicyEnabled(RateLimitPolicy.AUTH_RESEND_VERIFICATION);
      given(keyResolver.resendVerificationByEmail(email)).willReturn(key);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_RESEND_VERIFICATION, key)).willReturn(RateLimitDecision.allowed(4));

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkResendVerification(email)).doesNotThrowAnyException();
      verify(keyResolver).resendVerificationByEmail(email);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_RESEND_VERIFICATION, key);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }

    @Test
    @DisplayName("Deve delegar verificação de email corretamente")
    void shouldDelegateVerifyEmail() {
      // 1. Arrange
      var email = "user@example.com";
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_VERIFY_EMAIL, "hashed-email");

      givenPolicyEnabled(RateLimitPolicy.AUTH_VERIFY_EMAIL);
      given(keyResolver.verifyEmailByEmail(email)).willReturn(key);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_VERIFY_EMAIL, key)).willReturn(RateLimitDecision.allowed(4));

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkVerifyEmail(email)).doesNotThrowAnyException();
      verify(keyResolver).verifyEmailByEmail(email);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_VERIFY_EMAIL, key);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }
  }

  @Nested
  @DisplayName("Bloqueio de Rate Limit")
  class RateLimitDeniedTests {

    @Test
    @DisplayName("Deve lançar TooManyRequestsException quando consumo for negado")
    void shouldThrowTooManyRequestsWhenConsumptionIsDenied() {
      // 1. Arrange
      var email = "user@example.com";
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_RESEND_VERIFICATION, "hashed-email");
      var retryAfter = Duration.ofSeconds(30);

      givenPolicyEnabled(RateLimitPolicy.AUTH_RESEND_VERIFICATION);
      given(keyResolver.resendVerificationByEmail(email)).willReturn(key);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_RESEND_VERIFICATION, key))
          .willReturn(RateLimitDecision.denied(0, retryAfter.toNanos()));

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.checkResendVerification(email));

      // 3. Assert
      throwableAssert
          .isInstanceOf(TooManyRequestsException.class)
          .satisfies(exception -> assertThat(((TooManyRequestsException) exception).getRetryAfterInSeconds()).isEqualTo(30));
      verify(keyResolver).resendVerificationByEmail(email);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_RESEND_VERIFICATION, key);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }
  }

  @Nested
  @DisplayName("Falhas de Infraestrutura")
  class InfrastructureFailureTests {

    @Test
    @DisplayName("Deve propagar exceção quando failOpen estiver desabilitado")
    void shouldPropagateInfrastructureExceptionWhenFailOpenIsDisabled() {
      // 1. Arrange
      var email = "user@example.com";
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_VERIFY_EMAIL, "hashed-email");
      var exception = new RateLimitInfrastructureException(new RuntimeException("redis unavailable"));

      givenPolicyEnabled(RateLimitPolicy.AUTH_VERIFY_EMAIL);
      given(policyProperties.failOpen()).willReturn(false);
      given(keyResolver.verifyEmailByEmail(email)).willReturn(key);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_VERIFY_EMAIL, key)).willThrow(exception);

      // 2. Act
      var throwableAssert = assertThatThrownBy(() -> service.checkVerifyEmail(email));

      // 3. Assert
      throwableAssert.isSameAs(exception);
      verify(keyResolver).verifyEmailByEmail(email);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_VERIFY_EMAIL, key);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }

    @Test
    @DisplayName("Deve ignorar exceção de infraestrutura quando failOpen estiver habilitado")
    void shouldIgnoreInfrastructureExceptionWhenFailOpenIsEnabled() {
      // 1. Arrange
      var email = "user@example.com";
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_VERIFY_EMAIL, "hashed-email");
      var exception = new RateLimitInfrastructureException(new RuntimeException("redis unavailable"));

      givenPolicyEnabled(RateLimitPolicy.AUTH_VERIFY_EMAIL);
      given(policyProperties.failOpen()).willReturn(true);
      given(keyResolver.verifyEmailByEmail(email)).willReturn(key);
      given(rateLimiterPort.consume(RateLimitPolicy.AUTH_VERIFY_EMAIL, key)).willThrow(exception);

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkVerifyEmail(email)).doesNotThrowAnyException();
      verify(keyResolver).verifyEmailByEmail(email);
      verify(rateLimiterPort).consume(RateLimitPolicy.AUTH_VERIFY_EMAIL, key);
      verifyNoMoreInteractions(keyResolver, rateLimiterPort);
    }
  }

  @Nested
  @DisplayName("Bypass")
  class BypassTests {

    @Test
    @DisplayName("Deve ignorar consumo quando rate limit global estiver desabilitado")
    void shouldSkipConsumptionWhenGlobalRateLimitIsDisabled() {
      // 1. Arrange
      var email = "user@example.com";
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_VERIFY_EMAIL, "hashed-email");

      given(properties.enabled()).willReturn(false);
      given(keyResolver.verifyEmailByEmail(email)).willReturn(key);

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkVerifyEmail(email)).doesNotThrowAnyException();
      verify(keyResolver).verifyEmailByEmail(email);
      verifyNoInteractions(rateLimiterPort);
    }

    @Test
    @DisplayName("Deve ignorar consumo quando policy estiver desabilitada")
    void shouldSkipConsumptionWhenPolicyIsDisabled() {
      // 1. Arrange
      var email = "user@example.com";
      var key = RateLimitKey.createForEmail(RateLimitPolicy.AUTH_VERIFY_EMAIL, "hashed-email");

      given(properties.enabled()).willReturn(true);
      given(properties.getPolicyProperties(RateLimitPolicy.AUTH_VERIFY_EMAIL)).willReturn(policyProperties);
      given(policyProperties.enabled()).willReturn(false);
      given(keyResolver.verifyEmailByEmail(email)).willReturn(key);

      // 2. Act & 3. Assert
      assertThatCode(() -> service.checkVerifyEmail(email)).doesNotThrowAnyException();
      verify(keyResolver).verifyEmailByEmail(email);
      verifyNoInteractions(rateLimiterPort);
    }
  }

  private void givenPolicyEnabled(RateLimitPolicy policy) {
    given(properties.enabled()).willReturn(true);
    given(properties.getPolicyProperties(policy)).willReturn(policyProperties);
    given(policyProperties.enabled()).willReturn(true);
  }
}
