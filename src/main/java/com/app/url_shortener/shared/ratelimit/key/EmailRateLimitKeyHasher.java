package com.app.url_shortener.shared.ratelimit.key;

import com.app.url_shortener.shared.ratelimit.config.RateLimitProperties;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class EmailRateLimitKeyHasher {

  private static final String HASH_ALGORITHM = "HmacSHA256";
  private static final int MIN_SECRET_BYTES = 32;

  private final SecretKeySpec secretKeySpec;

  public EmailRateLimitKeyHasher(RateLimitProperties properties) {
    this.secretKeySpec = new SecretKeySpec(decodeSecret(properties), HASH_ALGORITHM);
  }

  public String hash(String email) {
    if (email == null || email.isBlank()) {
      throw new IllegalArgumentException("Email must not be null or blank");
    }

    String normalizedEmail = email.trim().toLowerCase(Locale.ROOT);

    try {
      Mac mac = Mac.getInstance(HASH_ALGORITHM);
      mac.init(secretKeySpec);
      byte[] encodedHash = mac.doFinal(normalizedEmail.getBytes(StandardCharsets.UTF_8));

      return HexFormat.of().formatHex(encodedHash);

    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException(
          "Could not generate email HMAC hash using " + HASH_ALGORITHM, e);
    }
  }

  private static byte[] decodeSecret(RateLimitProperties properties) {
    String secret = properties.emailHashSecret();

    if (secret == null || secret.isBlank()) {
      throw new IllegalStateException("Rate limit email hash secret must not be blank");
    }

    try {
      byte[] decodedSecretBytes = Base64.getDecoder().decode(secret.trim());

      if (decodedSecretBytes.length < MIN_SECRET_BYTES) {
        throw new IllegalStateException(
            "Rate limit email hash secret must decode to at least 32 bytes");
      }
      return decodedSecretBytes;

    } catch (IllegalArgumentException e) {
      throw new IllegalStateException("Rate limit email hash secret must be Base64 encoded", e);
    }
  }
}
