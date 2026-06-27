package com.app.url_shortener.iam.application.port.output;

import com.app.url_shortener.iam.domain.valueobject.VerificationCode;

public interface VerificationCodeProtectorPort {

  String hash(VerificationCode code);

  boolean matches(VerificationCode rawCode, String codeHash);

  String encrypt(VerificationCode code);

  VerificationCode decrypt(String encryptedCode);
}
