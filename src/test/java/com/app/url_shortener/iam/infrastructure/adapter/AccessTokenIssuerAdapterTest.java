package com.app.url_shortener.iam.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.app.url_shortener.iam.application.result.AuthenticatedUserResult;
import com.app.url_shortener.security.jwt.JwtAccessTokenSubject;
import com.app.url_shortener.security.jwt.JwtTokenService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador Emissor de Access Token")
class AccessTokenIssuerAdapterTest {

  @Mock private JwtTokenService jwtTokenService;

  @InjectMocks private AccessTokenIssuerAdapter adapter;

  @Nested
  @DisplayName("Emissão de Access Token")
  class IssueTests {

    @Test
    @DisplayName("Deve emitir token com dados do usuário e tempo de expiração")
    void shouldIssueTokenWithUserDataAndExpirationTime() {
      // 1. Arrange
      var user =
          new AuthenticatedUserResult(
              UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac100"),
              "User Name",
              "user@email.com",
              List.of("USER"),
              List.of("url:read", "url:write"),
              "FREE");
      var subject = new JwtAccessTokenSubject(user.id(), user.plan(), user.authorities());
      var accessToken = "jwt-access-token";
      var expiresInSeconds = 900L;
      when(jwtTokenService.generateAccessToken(subject)).thenReturn(accessToken);
      when(jwtTokenService.getExpiresInSeconds()).thenReturn(expiresInSeconds);

      // 2. Act
      var result = adapter.issue(user);

      // 3. Assert
      assertThat(result.value()).isEqualTo(accessToken);
      assertThat(result.expiresInSeconds()).isEqualTo(expiresInSeconds);

      var inOrder = inOrder(jwtTokenService);
      inOrder.verify(jwtTokenService).generateAccessToken(subject);
      inOrder.verify(jwtTokenService).getExpiresInSeconds();
      verifyNoMoreInteractions(jwtTokenService);
    }
  }
}
