package com.app.url_shortener.shared.ratelimit.key;

import com.app.url_shortener.shared.ratelimit.core.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Resolvedor de IP do Cliente")
class ClientIpResolverTest {

  private static final String X_FORWARDED_FOR = "X-Forwarded-For";
  private static final String X_REAL_IP = "X-Real-IP";
  private static final String UNKNOWN_CLIENT_IP = "unknown-client-ip";

  @Mock
  private HttpServletRequest request;

  @InjectMocks
  private ClientIpResolver resolver;

  @Nested
  @DisplayName("X-Forwarded-For")
  class ForwardedForTests {

    @Test
    @DisplayName("Deve retornar primeiro IPv4 válido do X-Forwarded-For")
    void shouldReturnFirstValidIpv4FromForwardedFor() {
      // 1. Arrange
      given(request.getHeader(X_FORWARDED_FOR)).willReturn("unknown, 203.0.113.10, 198.51.100.20");

      // 2. Act
      var result = resolver.resolve(request);

      // 3. Assert
      assertThat(result).isEqualTo("203.0.113.10");
      verify(request).getHeader(X_FORWARDED_FOR);
      verifyNoMoreInteractions(request);
    }

    @Test
    @DisplayName("Deve retornar primeiro IPv6 válido do X-Forwarded-For")
    void shouldReturnFirstValidIpv6FromForwardedFor() {
      // 1. Arrange
      given(request.getHeader(X_FORWARDED_FOR)).willReturn("example.com, 2001:db8::1, 203.0.113.10");

      // 2. Act
      var result = resolver.resolve(request);

      // 3. Assert
      assertThat(result).isEqualTo("2001:db8::1");
      verify(request).getHeader(X_FORWARDED_FOR);
      verifyNoMoreInteractions(request);
    }

    @Test
    @DisplayName("Deve ignorar hostnames no X-Forwarded-For")
    void shouldIgnoreHostnamesFromForwardedFor() {
      // 1. Arrange
      given(request.getHeader(X_FORWARDED_FOR)).willReturn("localhost, example.com");
      given(request.getHeader(X_REAL_IP)).willReturn("198.51.100.30");

      // 2. Act
      var result = resolver.resolve(request);

      // 3. Assert
      assertThat(result).isEqualTo("198.51.100.30");
      verify(request).getHeader(X_FORWARDED_FOR);
      verify(request).getHeader(X_REAL_IP);
      verifyNoMoreInteractions(request);
    }
  }

  @Nested
  @DisplayName("Fallbacks")
  class FallbackTests {

    @Test
    @DisplayName("Deve retornar X-Real-IP quando X-Forwarded-For não tiver IP válido")
    void shouldReturnRealIpWhenForwardedForHasNoValidIp() {
      // 1. Arrange
      given(request.getHeader(X_FORWARDED_FOR)).willReturn("invalid");
      given(request.getHeader(X_REAL_IP)).willReturn("2001:db8::2");

      // 2. Act
      var result = resolver.resolve(request);

      // 3. Assert
      assertThat(result).isEqualTo("2001:db8::2");
      verify(request).getHeader(X_FORWARDED_FOR);
      verify(request).getHeader(X_REAL_IP);
      verifyNoMoreInteractions(request);
    }

    @Test
    @DisplayName("Deve retornar remoteAddr quando headers não tiverem IP válido")
    void shouldReturnRemoteAddrWhenHeadersHaveNoValidIp() {
      // 1. Arrange
      given(request.getHeader(X_FORWARDED_FOR)).willReturn("invalid");
      given(request.getHeader(X_REAL_IP)).willReturn("localhost");
      given(request.getRemoteAddr()).willReturn("203.0.113.40");

      // 2. Act
      var result = resolver.resolve(request);

      // 3. Assert
      assertThat(result).isEqualTo("203.0.113.40");
      verify(request).getHeader(X_FORWARDED_FOR);
      verify(request).getHeader(X_REAL_IP);
      verify(request).getRemoteAddr();
      verifyNoMoreInteractions(request);
    }

    @Test
    @DisplayName("Deve retornar valor desconhecido quando request for nula")
    void shouldReturnUnknownWhenRequestIsNull() {
      // 1. Arrange

      // 2. Act
      var result = resolver.resolve(null);

      // 3. Assert
      assertThat(result).isEqualTo(UNKNOWN_CLIENT_IP);
    }

    @Test
    @DisplayName("Deve retornar valor desconhecido quando nenhuma origem tiver IP válido")
    void shouldReturnUnknownWhenNoSourceHasValidIp() {
      // 1. Arrange
      given(request.getHeader(X_FORWARDED_FOR)).willReturn("invalid");
      given(request.getHeader(X_REAL_IP)).willReturn("example.com");
      given(request.getRemoteAddr()).willReturn("localhost");

      // 2. Act
      var result = resolver.resolve(request);

      // 3. Assert
      assertThat(result).isEqualTo(UNKNOWN_CLIENT_IP);
      verify(request).getHeader(X_FORWARDED_FOR);
      verify(request).getHeader(X_REAL_IP);
      verify(request).getRemoteAddr();
      verifyNoMoreInteractions(request);
    }
  }
}
