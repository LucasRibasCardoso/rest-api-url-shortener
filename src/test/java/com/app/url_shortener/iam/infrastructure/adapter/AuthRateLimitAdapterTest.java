package com.app.url_shortener.iam.infrastructure.adapter;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.shared.ratelimit.service.RateLimitService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adapter de rate limit de autenticação")
class AuthRateLimitAdapterTest {

  @Mock private RateLimitService rateLimitService;

  @InjectMocks private AuthRateLimitAdapter adapter;

  @Test
  @DisplayName("Deve delegar verificação de rate limit do cadastro com IP e email")
  void shouldDelegateRegisterRateLimitWithIpAndEmail() {
    // 1. Arrange
    String clientIp = "203.0.113.10";
    String email = "user@example.com";

    // 2. Act
    adapter.checkRegister(clientIp, email);

    // 3. Assert
    verify(rateLimitService).checkRegister(clientIp, email);
    verifyNoMoreInteractions(rateLimitService);
  }
}
