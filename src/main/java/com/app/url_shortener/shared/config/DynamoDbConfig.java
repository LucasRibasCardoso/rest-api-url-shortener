package com.app.url_shortener.shared.config;

import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {

  @Bean
  public DynamoDbClient dynamoDbClient(DynamoDbProperties properties) {
    var awsBasicCredentials = AwsBasicCredentials.create(properties.accessKey(), properties.secretKey());

    return DynamoDbClient.builder()
        .endpointOverride(URI.create(properties.endpoint()))
        .region(Region.of(properties.region()))
        .credentialsProvider(StaticCredentialsProvider.create(awsBasicCredentials))
        .build();
  }

  @Bean
  public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
    return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
  }

  @Bean
  public DynamoDbTable<UrlEntity> urlTable(
      DynamoDbEnhancedClient dynamoDbEnhancedClient,
      DynamoDbProperties properties) {

    return dynamoDbEnhancedClient.table(
        properties.tables().url(), TableSchema.fromImmutableClass(UrlEntity.class));
  }
}
