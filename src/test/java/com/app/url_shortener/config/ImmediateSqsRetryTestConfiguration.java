package com.app.url_shortener.config;

import io.awspring.cloud.sqs.listener.errorhandler.AsyncErrorHandler;
import io.awspring.cloud.sqs.listener.errorhandler.ImmediateRetryAsyncErrorHandler;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

@TestConfiguration(proxyBeanMethods = false)
public class ImmediateSqsRetryTestConfiguration {

  @Bean
  AsyncErrorHandler<Object> immediateSqsRetryAsyncErrorHandler() {
    return new ImmediateRetryAsyncErrorHandler<>();
  }
}
