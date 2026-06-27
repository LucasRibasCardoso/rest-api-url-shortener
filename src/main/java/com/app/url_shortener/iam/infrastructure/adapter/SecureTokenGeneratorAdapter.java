package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.SecureTokenGeneratorPort;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecureTokenGeneratorAdapter implements SecureTokenGeneratorPort {

  private static final int TOKEN_LENGTH_BYTES = 32;
  private static final String HASH_ALGORITHM = "SHA-256";
  private final SecureRandom secureRandom;

  public SecureTokenGeneratorAdapter() {
    this.secureRandom = new SecureRandom();
  }

  @Override
  public String generateRandomToken() {
    byte[] randomBytes = new byte[TOKEN_LENGTH_BYTES];
    secureRandom.nextBytes(randomBytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
  }

  @Override
  public String hashToken(String rawToken) {
    if (rawToken == null || rawToken.isBlank()) {
      throw new IllegalArgumentException("Não é possível gerar hash de um token vazio ou nulo");
    }

    try {
      MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
      byte[] encodedHash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
      return HexFormat.of().formatHex(encodedHash);

    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException(
          "Algoritmo de hash não encontrado no ambiente: " + HASH_ALGORITHM, e);
    }
  }
}
