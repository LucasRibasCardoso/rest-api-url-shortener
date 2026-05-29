package com.app.url_shortener.shared.config;

import com.app.url_shortener.security.config.JwtProperties;
import com.app.url_shortener.shared.idempotency.config.IdempotencyProperties;
import com.app.url_shortener.shared.ratelimit.config.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties({
  JwtProperties.class,
  IdempotencyProperties.class,
  RateLimitProperties.class
})
@Configuration
public class PropertiesConfig {}
