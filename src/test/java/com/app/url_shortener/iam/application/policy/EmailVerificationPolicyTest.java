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
  void shouldRejectNullOrNonPositiveDurations(Duration codeTtl, String message) {
    // 1. Arrange

    // 2. Act & 3. Assert
    assertThatThrownBy(() -> new EmailVerificationPolicy(codeTtl))
        .hasMessage(message);
  }

  private static java.util.stream.Stream<Arguments> invalidPolicies() {
    return java.util.stream.Stream.of(
        Arguments.of(null, "codeTtl must not be null"),
        Arguments.of(Duration.ZERO, "codeTtl must be positive"),
        Arguments.of(Duration.ofSeconds(-1), "codeTtl must be positive"));
  }
}
