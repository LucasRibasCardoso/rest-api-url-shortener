package com.app.url_shortener;

import com.app.url_shortener.config.PostgresContainerSupport;
import com.app.url_shortener.config.RedisContainerSupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest
@ActiveProfiles("test")
class UrlShortenerApplicationTests {

	@DynamicPropertySource
	static void registerDynamicProperties(DynamicPropertyRegistry registry) {
		PostgresContainerSupport.registerDatasourceProperties(registry);
		RedisContainerSupport.registerRedisProperties(registry);
	}

	@Test
	void contextLoads() {
	}

	@TestConfiguration
	static class TestCacheConfig {
		@Bean
		CacheManager cacheManager() {
			return new ConcurrentMapCacheManager();
		}
	}

}
