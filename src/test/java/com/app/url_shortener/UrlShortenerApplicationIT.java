package com.app.url_shortener;

import com.app.url_shortener.config.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;

class UrlShortenerApplicationIT extends AbstractIntegrationTest {

  @Test
  void contextLoads() {}

  @TestConfiguration
  static class TestCacheConfig {
    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager();
    }
  }
}
