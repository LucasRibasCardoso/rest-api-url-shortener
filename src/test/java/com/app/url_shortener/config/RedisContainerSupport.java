package com.app.url_shortener.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.utility.DockerImageName;

import static org.testcontainers.utility.DockerImageName.parse;

public final class RedisContainerSupport {

  private static final DockerImageName REDIS_IMAGE = parse("redis:7.2-alpine");

  private static final GenericContainer<?> REDIS_CONTAINER = new GenericContainer<>(REDIS_IMAGE).withExposedPorts(6379);

  static {
    REDIS_CONTAINER.start();
  }

  private RedisContainerSupport() {
  }

  public static void registerRedisProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", REDIS_CONTAINER::getHost);
    registry.add("spring.data.redis.port", () -> REDIS_CONTAINER.getMappedPort(6379));
  }
}
