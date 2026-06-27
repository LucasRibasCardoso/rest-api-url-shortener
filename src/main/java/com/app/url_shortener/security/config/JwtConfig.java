package com.app.url_shortener.security.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.nio.charset.StandardCharsets;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
public class JwtConfig {

  @Bean
  public JwtDecoder jwtDecoder(JwtProperties properties) {
    SecretKey key = createSecretKey(properties.secret());
    return NimbusJwtDecoder.withSecretKey(key).build();
  }

  @Bean
  public JwtEncoder jwtEncoder(JwtProperties properties) {
    SecretKey key = createSecretKey(properties.secret());
    return new NimbusJwtEncoder(new ImmutableSecret<>(key));
  }

  private SecretKey createSecretKey(String secret) {
    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("JWT secret must not be blank.");
    }

    byte[] secretBytes = secret.trim().getBytes(StandardCharsets.UTF_8);

    if (secretBytes.length < 32) {
      throw new IllegalStateException("JWT secret must have at least 256 bits.");
    }

    return new SecretKeySpec(secretBytes, "HmacSHA256");
  }
}
