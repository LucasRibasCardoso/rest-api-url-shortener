package com.app.url_shortener.iam.infrastructure.config;

import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.encrypt.Encryptors;
import org.springframework.security.crypto.encrypt.TextEncryptor;

@Configuration
@EnableConfigurationProperties({EmailVerificationProperties.class})
public class EmailVerificationConfig {

  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int MIN_HMAC_SECRET_BYTES = 32;

  @Bean
  public EmailVerificationPolicy emailVerificationPolicy(EmailVerificationProperties properties) {
    return new EmailVerificationPolicy(
        properties.codeTtl(), properties.sendingTimeout(), properties.resendCooldown());
  }

  @Bean
  public TextEncryptor verificationCodeTextEncryptor(EmailVerificationProperties properties) {
    var codeProtection = properties.codeProtection();
    return Encryptors.text(codeProtection.encryptionPassword(), codeProtection.encryptionSalt());
  }

  @Bean
  public SecretKeySpec verificationCodeHmacSecretKeySpec(EmailVerificationProperties properties) {
    return new SecretKeySpec(decodeHmacSecret(properties), HMAC_ALGORITHM);
  }

  private byte[] decodeHmacSecret(EmailVerificationProperties properties) {
    String hmacSecret = properties.codeProtection().hmacSecret();

    try {
      byte[] decodedSecretBytes = Base64.getDecoder().decode(hmacSecret.trim());

      if (decodedSecretBytes.length < MIN_HMAC_SECRET_BYTES) {
        throw new IllegalStateException(
            "Email verification HMAC secret must decode to at least 32 bytes");
      }

      return decodedSecretBytes;
    } catch (IllegalArgumentException exception) {
      throw new IllegalStateException(
          "Email verification HMAC secret must be Base64 encoded", exception);
    }
  }
}
