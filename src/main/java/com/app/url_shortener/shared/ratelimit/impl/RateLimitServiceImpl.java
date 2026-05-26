package com.app.url_shortener.shared.ratelimit.impl;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.shared.config.properties.RateLimitPolicyProperties;
import com.app.url_shortener.shared.config.properties.RateLimitProperties;
import com.app.url_shortener.shared.exception.ratelimit.RateLimitInfrastructureException;
import com.app.url_shortener.shared.exception.ratelimit.TooManyRequestsException;
import com.app.url_shortener.shared.ratelimit.RateLimitService;
import com.app.url_shortener.shared.ratelimit.core.RateLimitDecision;
import com.app.url_shortener.shared.ratelimit.core.RateLimitPolicy;
import com.app.url_shortener.shared.ratelimit.core.RateLimiterPort;
import com.app.url_shortener.shared.ratelimit.key.RateLimitKey;
import com.app.url_shortener.shared.ratelimit.key.RateLimitKeyResolver;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RateLimitServiceImpl implements RateLimitService {

  private static final Logger logger = LoggerFactory.getLogger(RateLimitServiceImpl.class);

  private final RateLimiterPort rateLimiterPort;
  private final RateLimitProperties properties;
  private final RateLimitKeyResolver keyResolver;

  @Override
  public void checkLogin(String clientIp, String email) {
    Objects.requireNonNull(clientIp, "clientIp must not be null");
    Objects.requireNonNull(email, "email must not be null");

    check(RateLimitPolicy.AUTH_LOGIN, keyResolver.loginByEmail(email));
    check(RateLimitPolicy.AUTH_LOGIN, keyResolver.loginByIpAndEmail(clientIp, email));
  }

  @Override
  public void checkShorten(UUID userId, PlanType plan) {
    Objects.requireNonNull(userId, "userId must not be null");
    Objects.requireNonNull(plan, "plan must not be null");

    RateLimitPolicy policy =
        switch (plan) {
          case FREE -> RateLimitPolicy.URL_SHORTEN_FREE;
          case PREMIUM -> RateLimitPolicy.URL_SHORTEN_PREMIUM;
        };

    check(policy, keyResolver.shortenByUserIdAndPlan(userId, policy));
  }

  @Override
  public void checkResendVerification(String email) {
    Objects.requireNonNull(email, "email must not be null");
    check(RateLimitPolicy.AUTH_RESEND_VERIFICATION, keyResolver.resendVerificationByEmail(email));
  }

  @Override
  public void checkVerifyEmail(String email) {
    Objects.requireNonNull(email, "email must not be null");
    check(RateLimitPolicy.AUTH_VERIFY_EMAIL, keyResolver.verifyEmailByEmail(email));
  }

  /**
   * Verifica a política de rate limit informada e tenta consumir um token.
   * Se o consumo não for permitido, lança {@link TooManyRequestsException}.
   * Se ocorrer falha de infraestrutura e {@code failOpen} estiver habilitado,
   * a falha é ignorada.
   *
   * @param policy política de rate limit a ser verificada
   * @param key chave de rate limit a ser consumida
   * @throws TooManyRequestsException quando o consumo não é permitido
   * @throws RateLimitInfrastructureException quando ocorre falha de infraestrutura e failOpen está desabilitado
   */
  private void check(RateLimitPolicy policy, RateLimitKey key) {
    Objects.requireNonNull(policy, "policy must not be null");
    Objects.requireNonNull(key, "key must not be null");

    if (!properties.enabled()) return;

    RateLimitPolicyProperties policyProperties = properties.getPolicyProperties(policy);
    if (!policyProperties.enabled()) return;

    try {
      RateLimitDecision decision = rateLimiterPort.consume(policy, key);
      if (!decision.allowed()) {
        throw new TooManyRequestsException(decision.retryAfter());
      }

    } catch (RateLimitInfrastructureException e) {
      if (policyProperties.failOpen()) {
        logger.warn(
            "Rate limit infrastructure failure ignored because failOpen is enabled. policy={}, cause={}, message={}",
            policy,
            e.getClass().getSimpleName(),
            e.getMessage());
        return;
      }
      throw e;
    }
  }
}
