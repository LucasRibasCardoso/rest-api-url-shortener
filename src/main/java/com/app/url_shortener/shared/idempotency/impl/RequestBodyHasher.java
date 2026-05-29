package com.app.url_shortener.shared.idempotency.impl;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class RequestBodyHasher {

  private static final String SHA_256 = "SHA-256";

  public String sha256Hex(byte[] body) {
    try {
      MessageDigest digest = MessageDigest.getInstance(SHA_256);
      return HexFormat.of().formatHex(digest.digest(body));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 algorithm is not available", exception);
    }
  }
}
