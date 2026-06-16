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

  @ParameterizedTest
  @MethodSource("invalidPolicies")
  @DisplayName("Deve rejeitar durações nulas ou não positivas")
  void shouldRejectNullOrNonPositiveDurations(
      Duration codeTtl, Duration idempotencyTtl, Duration processingLeaseTtl, String message) {
    // 1. Arrange

    // 2. Act & 3. Assert
    assertThatThrownBy(
            () -> new EmailVerificationPolicy(codeTtl, idempotencyTtl, processingLeaseTtl))
        .hasMessage(message);
  }

  private static java.util.stream.Stream<Arguments> invalidPolicies() {
    return java.util.stream.Stream.of(
        Arguments.of(null, VALID_TTL, VALID_TTL, "codeTtl must not be null"),
        Arguments.of(VALID_TTL, null, VALID_TTL, "idempotencyTtl must not be null"),
        Arguments.of(VALID_TTL, VALID_TTL, null, "processingLeaseTtl must not be null"),
        Arguments.of(Duration.ZERO, VALID_TTL, VALID_TTL, "codeTtl must be positive"),
        Arguments.of(VALID_TTL, Duration.ofSeconds(-1), VALID_TTL, "idempotencyTtl must be positive"),
        Arguments.of(
            VALID_TTL, VALID_TTL, Duration.ZERO, "processingLeaseTtl must be positive"));
  }
}
