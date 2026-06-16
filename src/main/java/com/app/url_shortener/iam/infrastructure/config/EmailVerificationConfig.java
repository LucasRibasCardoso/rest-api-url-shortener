package com.app.url_shortener.iam.infrastructure.config;

import com.app.url_shortener.iam.application.policy.EmailVerificationPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(EmailVerificationProperties.class)
public class EmailVerificationConfig {

  @Bean
  public EmailVerificationPolicy emailVerificationPolicy(EmailVerificationProperties properties) {
    return new EmailVerificationPolicy(properties.codeTtl());
  }
}
