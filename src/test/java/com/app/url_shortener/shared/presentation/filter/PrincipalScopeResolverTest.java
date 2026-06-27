package com.app.url_shortener.shared.presentation.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.iam.domain.enums.PlanType;
import com.app.url_shortener.iam.domain.enums.UserStatus;
import com.app.url_shortener.security.principal.UserPrincipal;
import com.app.url_shortener.shared.idempotency.impl.PrincipalScopeResolver;
import com.app.url_shortener.shared.ratelimit.core.ClientIpResolver;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - PrincipalScopeResolver")
class PrincipalScopeResolverTest {

  @Mock private ClientIpResolver clientIpResolver;

  @InjectMocks private PrincipalScopeResolver resolver;

  @AfterEach
  void tearDown() {
    SecurityContextHolder.clearContext();
  }

  @Nested
  @DisplayName("resolve")
  class ResolveTests {

    @Test
    @DisplayName("Deve resolver escopo pelo UserPrincipal autenticado")
    void shouldResolveScopeFromAuthenticatedUserPrincipal() {
      // 1. Arrange
      var request = new MockHttpServletRequest();
      var userId = UUID.fromString("4c45f6b4-4a1a-4f75-93e0-2166cc6f2026");
      var principal = userPrincipal(userId);
      var authentication =
          new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
      SecurityContextHolder.getContext().setAuthentication(authentication);

      // 2. Act
      var result = resolver.resolve(request);

      // 3. Assert
      assertThat(result).isEqualTo("user:" + userId);
      verifyNoInteractions(clientIpResolver);
    }

    @Test
    @DisplayName("Deve resolver escopo pelo IP quando não houver usuário autenticado")
    void shouldResolveScopeFromIpWhenAuthenticationIsMissing() {
      // 1. Arrange
      var request = new MockHttpServletRequest();
      given(clientIpResolver.resolve(request)).willReturn("127.0.0.1");

      // 2. Act
      var result = resolver.resolve(request);

      // 3. Assert
      assertThat(result).isEqualTo("ip:127.0.0.1");
      verify(clientIpResolver).resolve(request);
      verifyNoMoreInteractions(clientIpResolver);
    }
  }

  private static UserPrincipal userPrincipal(UUID userId) {
    return new UserPrincipal(
        userId, "User", "user@example.com", "hash", PlanType.FREE, UserStatus.ACTIVE, List.of());
  }
}
