package com.app.url_shortener.iam.infrastructure.config;

import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.regions.providers.AwsRegionProvider;
import software.amazon.awssdk.services.ses.SesClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(
    name = "app.iam.email-verification.sender",
    havingValue = "ses")
@EnableConfigurationProperties(AwsSesEmailVerificationProperties.class)
public class AwsSesEmailVerificationConfig {

  @Bean
  public SesClient sesClient(
      AwsRegionProvider regionProvider,
      AwsCredentialsProvider credentialsProvider,
      AwsSesEmailVerificationProperties properties) {

    var overrideConfiguration = ClientOverrideConfiguration.builder()
            .apiCallTimeout(properties.apiCallTimeout())
            .build();

    var builder = SesClient.builder()
            .credentialsProvider(credentialsProvider)
            .region(regionProvider.getRegion())
            .overrideConfiguration(overrideConfiguration);

    if (properties.endpoint() != null && !properties.endpoint().isBlank()) {
      builder.endpointOverride(URI.create(properties.endpoint().trim()));
    }

    return builder.build();
  }
}
