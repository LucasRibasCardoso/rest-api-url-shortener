package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.port.output.UrlEncoderPort;
import com.app.url_shortener.url.infrastructure.config.HashidsProperties;
import jakarta.annotation.PostConstruct;
import org.hashids.Hashids;
import org.springframework.stereotype.Component;

@Component
public class UrlEncoderAdapter implements UrlEncoderPort {

  private static final String BASE62_ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

  private final String salt;
  private final int minLength;
  private Hashids hashids;

  public UrlEncoderAdapter(HashidsProperties properties) {
    this.salt = properties.salt();
    this.minLength = properties.minLength();
  }

  @PostConstruct
  public void initialize() {
    this.hashids = new Hashids(salt, minLength, BASE62_ALPHABET);
  }

  @Override
  public String encode(Long id) {
    return hashids.encode(id);
  }
}
