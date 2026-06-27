package com.app.url_shortener.config;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.data.redis.test.autoconfigure.DataRedisTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataRedisTest
public abstract class BaseRedisSliceTest {

  @BeforeEach
  void resetRedis() {
    RedisContainerSupport.resetRedis();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    RedisContainerSupport.registerRedisProperties(registry);
  }
}
