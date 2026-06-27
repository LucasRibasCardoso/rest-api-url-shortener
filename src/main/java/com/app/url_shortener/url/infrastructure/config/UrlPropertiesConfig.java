package com.app.url_shortener.url.infrastructure.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
  HashidsProperties.class,
  IdGeneratorProperties.class,
  RedirectCacheProperties.class
})
public class UrlPropertiesConfig {}
