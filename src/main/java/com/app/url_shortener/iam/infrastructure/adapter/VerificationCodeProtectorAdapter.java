package com.app.url_shortener.iam.infrastructure.adapter;

import com.app.url_shortener.iam.application.port.output.VerificationCodeProtectorPort;
import com.app.url_shortener.iam.domain.exception.IamErrorCode;
import com.app.url_shortener.iam.domain.exception.auth.VerificationCodeProtectionException;
import com.app.url_shortener.iam.domain.valueobject.VerificationCode;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.encrypt.TextEncryptor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VerificationCodeProtectorAdapter implements VerificationCodeProtectorPort {

  private static final String HMAC_ALGORITHM = "HmacSHA256";

  private final SecretKeySpec verificationCodeHmacSecretKeySpec;
  private final TextEncryptor verificationCodeTextEncryptor;

  @Override
  public String hash(VerificationCode code) {
    requireCode(code);

    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(verificationCodeHmacSecretKeySpec);

      byte[] digest = mac.doFinal(code.value().getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(digest);
    } catch (GeneralSecurityException exception) {
      throw new VerificationCodeProtectionException(IamErrorCode.AUTH_VERIFICATION_CODE_HASH_FAILED, exception);
    }
  }

  @Override
  public boolean matches(VerificationCode rawCode, String codeHash) {
    requireCode(rawCode);

    if (codeHash == null || codeHash.isBlank()) {
      return false;
    }

    String candidateHash = hash(rawCode);
    return MessageDigest.isEqual(
            candidateHash.getBytes(StandardCharsets.UTF_8),
            codeHash.getBytes(StandardCharsets.UTF_8));
  }

  @Override
  public String encrypt(VerificationCode code) {
    requireCode(code);

    try {
      return verificationCodeTextEncryptor.encrypt(code.value());

    } catch (RuntimeException exception) {
      throw new VerificationCodeProtectionException(IamErrorCode.AUTH_VERIFICATION_CODE_ENCRYPT_FAILED, exception);
    }
  }

  @Override
  public VerificationCode decrypt(String encryptedCode) {
    if (encryptedCode == null || encryptedCode.isBlank()) {
      throw new VerificationCodeProtectionException(IamErrorCode.AUTH_ENCRYPTED_VERIFICATION_CODE_INVALID);
    }

    try {
      String rawCode = verificationCodeTextEncryptor.decrypt(encryptedCode);
      return VerificationCode.of(rawCode);

    } catch (RuntimeException exception) {
      throw new VerificationCodeProtectionException(IamErrorCode.AUTH_VERIFICATION_CODE_DECRYPT_FAILED, exception);
    }
  }

  private void requireCode(VerificationCode code) {
    if (code == null) {
      throw new VerificationCodeProtectionException(IamErrorCode.AUTH_VERIFICATION_CODE_INVALID);
    }
  }
}
