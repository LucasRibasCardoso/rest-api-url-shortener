package com.app.url_shortener.shared.config;

import com.app.url_shortener.security.config.JwtProperties;
import com.app.url_shortener.shared.config.properties.IdempotencyProperties;
import com.app.url_shortener.shared.config.properties.RateLimitProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@EnableConfigurationProperties({
  JwtProperties.class,
  IdempotencyProperties.class,
  RateLimitProperties.class
})
@Configuration
public class PropertiesConfig {}
