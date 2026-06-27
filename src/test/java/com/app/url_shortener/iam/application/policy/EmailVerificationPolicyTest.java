package com.app.url_shortener.iam.application.policy;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@Tag("unit")
@DisplayName("Testes de Unidade - Política de Verificação de Email")
class EmailVerificationPolicyTest {

  private static final Duration VALID_TTL = Duration.ofMinutes(1);
  private static final Duration VALID_SENDING_TIMEOUT = Duration.ofMinutes(1);
  private static final Duration VALID_RESEND_COOLDOWN = Duration.ofSeconds(30);

  @ParameterizedTest
  @MethodSource("invalidPolicies")
  @DisplayName("Deve rejeitar durações nulas ou não positivas")
  void shouldRejectNullOrNonPositiveDurations(
      Duration codeTtl, Duration sendingTimeout, Duration resendCooldown, String message) {
    // 1. Arrange

    // 2. Act & 3. Assert
    assertThatThrownBy(() -> new EmailVerificationPolicy(codeTtl, sendingTimeout, resendCooldown))
        .hasMessage(message);
  }

  private static java.util.stream.Stream<Arguments> invalidPolicies() {
    return java.util.stream.Stream.of(
        Arguments.of(
            null, VALID_SENDING_TIMEOUT, VALID_RESEND_COOLDOWN, "codeTtl must not be null"),
        Arguments.of(VALID_TTL, null, VALID_RESEND_COOLDOWN, "sendingTimeout must not be null"),
        Arguments.of(VALID_TTL, VALID_SENDING_TIMEOUT, null, "resendCooldown must not be null"),
        Arguments.of(
            Duration.ZERO,
            VALID_SENDING_TIMEOUT,
            VALID_RESEND_COOLDOWN,
            "codeTtl must be positive"),
        Arguments.of(
            Duration.ofSeconds(-1),
            VALID_SENDING_TIMEOUT,
            VALID_RESEND_COOLDOWN,
            "codeTtl must be positive"),
        Arguments.of(
            VALID_TTL, Duration.ZERO, VALID_RESEND_COOLDOWN, "sendingTimeout must be positive"),
        Arguments.of(
            VALID_TTL,
            Duration.ofSeconds(-1),
            VALID_RESEND_COOLDOWN,
            "sendingTimeout must be positive"),
        Arguments.of(
            VALID_TTL, VALID_SENDING_TIMEOUT, Duration.ZERO, "resendCooldown must be positive"),
        Arguments.of(
            VALID_TTL,
            VALID_SENDING_TIMEOUT,
            Duration.ofSeconds(-1),
            "resendCooldown must be positive"));
  }
}
