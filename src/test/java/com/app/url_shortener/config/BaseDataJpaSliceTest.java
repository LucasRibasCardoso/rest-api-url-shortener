package com.app.url_shortener.config;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class BaseDataJpaSliceTest {

  @BeforeEach
  void resetDatabase() {
    PostgresContainerSupport.resetDatabase();
  }

  @DynamicPropertySource
  static void registerProperties(DynamicPropertyRegistry registry) {
    PostgresContainerSupport.registerDatasourceProperties(registry);
  }
}
