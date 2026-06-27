package com.app.url_shortener.security.jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.app.url_shortener.security.config.JwtProperties;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - JwtTokenService")
class JwtTokenServiceTest {

  @Mock private JwtEncoder jwtEncoder;

  @Mock private JwtProperties jwtProperties;

  @Captor private ArgumentCaptor<JwtEncoderParameters> jwtEncoderParametersCaptor;

  @InjectMocks private JwtTokenService jwtTokenService;

  @Nested
  @DisplayName("Geração de Access Token")
  class GenerateAccessTokenTests {

    @Test
    @DisplayName("Deve enviar headers e claims corretos para o encoder")
    void shouldSendCorrectHeadersAndClaimsToEncoder() {
      // 1. Arrange
      var issuer = "app-url-shortener";
      var expirationSeconds = 900L;
      var userId = UUID.randomUUID();
      var authorities = List.of("ROLE_USER", "url:create", "url:read");
      var subject = new JwtAccessTokenSubject(userId, "PREMIUM", authorities);
      var encodedJwt = jwt();

      given(jwtProperties.issuer()).willReturn(issuer);
      given(jwtProperties.accessTokenExpirationSeconds()).willReturn(expirationSeconds);
      given(jwtEncoder.encode(jwtEncoderParametersCaptor.capture())).willReturn(encodedJwt);

      // 2. Act
      var token = jwtTokenService.generateAccessToken(subject);

      // 3. Assert
      assertThat(token).isEqualTo("encoded-access-token");

      var capturedParameters = jwtEncoderParametersCaptor.getValue();
      var capturedHeader = capturedParameters.getJwsHeader();
      var capturedClaims = capturedParameters.getClaims();
      String capturedPlan = capturedClaims.getClaim("plan");
      List<String> capturedAuthorities = capturedClaims.getClaim("authorities");

      assertAll(
          () -> assertThat(capturedHeader.getAlgorithm()).isEqualTo(MacAlgorithm.HS256),
          () -> assertThat(capturedClaims.getSubject()).isEqualTo(userId.toString()),
          () -> assertThat(capturedPlan).isEqualTo("PREMIUM"),
          () -> assertThat(capturedAuthorities).isEqualTo(authorities),
          () -> assertThat(capturedClaims.getClaims()).containsEntry("iss", issuer),
          () -> assertThat(capturedClaims.getIssuedAt()).isNotNull(),
          () ->
              assertThat(capturedClaims.getExpiresAt())
                  .isEqualTo(capturedClaims.getIssuedAt().plusSeconds(expirationSeconds)));

      verify(jwtProperties).accessTokenExpirationSeconds();
      verify(jwtProperties).issuer();
      verify(jwtEncoder).encode(capturedParameters);
      verifyNoMoreInteractions(jwtProperties, jwtEncoder);
    }
  }

  private static Jwt jwt() {
    return new Jwt(
        "encoded-access-token",
        Instant.now(),
        Instant.now().plusSeconds(900),
        Map.of("alg", "HS256"),
        Map.of("sub", "user-id"));
  }
}
