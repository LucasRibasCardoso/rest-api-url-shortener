package com.app.url_shortener.security.jwt;

import com.app.url_shortener.security.config.JwtProperties;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtTokenService {

  private final JwtEncoder jwtEncoder;
  private final JwtProperties jwtProperties;

  public String generateAccessToken(JwtAccessTokenSubject tokenProperties) {
    Instant now = Instant.now();
    Instant expiresAt = now.plus(jwtProperties.accessTokenExpirationSeconds(), ChronoUnit.SECONDS);

    JwtClaimsSet claims =
        JwtClaimsSet.builder()
            .issuer(jwtProperties.issuer())
            .issuedAt(now)
            .expiresAt(expiresAt)
            .subject(tokenProperties.id().toString())
            .claim("plan", tokenProperties.plan().toUpperCase())
            .claim("authorities", tokenProperties.authorities())
            .build();

    JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();

    return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
  }

  public long getExpiresInSeconds() {
    return jwtProperties.accessTokenExpirationSeconds();
  }
}
