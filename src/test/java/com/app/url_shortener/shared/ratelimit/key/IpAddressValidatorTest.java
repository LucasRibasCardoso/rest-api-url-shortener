package com.app.url_shortener.shared.ratelimit.key;

import static org.assertj.core.api.Assertions.assertThat;

import com.app.url_shortener.shared.ratelimit.core.IpAddressValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Validador de Endereço IP")
class IpAddressValidatorTest {

  @Nested
  @DisplayName("IPv4")
  class Ipv4Tests {

    @ParameterizedTest
    @ValueSource(strings = {"127.0.0.1", "192.168.1.10", "203.0.113.10", " 8.8.8.8 "})
    @DisplayName("Deve aceitar IPv4 válido")
    void shouldAcceptValidIpv4(String value) {
      // 1. Arrange

      // 2. Act
      var result = IpAddressValidator.isValidIpv4(value);

      // 3. Assert
      assertThat(result).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "256.1.1.1", "1.2.3", "localhost", "example.com", "2001:db8::1"})
    @DisplayName("Deve rejeitar valor que não seja IPv4 válido")
    void shouldRejectInvalidIpv4(String value) {
      // 1. Arrange

      // 2. Act
      var result = IpAddressValidator.isValidIpv4(value);

      // 3. Assert
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("IPv6")
  class Ipv6Tests {

    @ParameterizedTest
    @ValueSource(
        strings = {"::1", "2001:db8::1", "2001:0db8:85a3:0000:0000:8a2e:0370:7334", " fe80::1 "})
    @DisplayName("Deve aceitar IPv6 válido")
    void shouldAcceptValidIpv6(String value) {
      // 1. Arrange

      // 2. Act
      var result = IpAddressValidator.isValidIpv6(value);

      // 3. Assert
      assertThat(result).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(
        strings = {" ", "2001:db8:::1", "12345::1", "localhost", "example.com", "203.0.113.10"})
    @DisplayName("Deve rejeitar valor que não seja IPv6 válido")
    void shouldRejectInvalidIpv6(String value) {
      // 1. Arrange

      // 2. Act
      var result = IpAddressValidator.isValidIpv6(value);

      // 3. Assert
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("IP genérico")
  class GenericIpTests {

    @ParameterizedTest
    @ValueSource(strings = {"203.0.113.10", "2001:db8::1"})
    @DisplayName("Deve aceitar IPv4 ou IPv6 válido")
    void shouldAcceptValidIpv4OrIpv6(String value) {
      // 1. Arrange

      // 2. Act
      var result = IpAddressValidator.isValidIp(value);

      // 3. Assert
      assertThat(result).isTrue();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "localhost", "example.com", "999.999.999.999", "2001:db8:::1"})
    @DisplayName("Deve rejeitar valor que não seja IP válido")
    void shouldRejectInvalidIp(String value) {
      // 1. Arrange

      // 2. Act
      var result = IpAddressValidator.isValidIp(value);

      // 3. Assert
      assertThat(result).isFalse();
    }
  }
}
