package com.app.url_shortener.shared.config;

import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
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
  public DynamoDbClient dynamoDbClient(
      @Value("${aws.dynamodb.endpoint}") String endpoint,
      @Value("${aws.dynamodb.region}") String region,
      @Value("${aws.dynamodb.access-key}") String accessKey,
      @Value("${aws.dynamodb.secret-key}") String secretKey) {

    return DynamoDbClient.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of(region))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
        .build();
  }

  @Bean
  public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
    return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
  }

  @Bean
  public DynamoDbTable<UrlEntity> urlTable(
      DynamoDbEnhancedClient dynamoDbEnhancedClient,
      @Value("${aws.dynamodb.tables.url}") String tableName) {

    return dynamoDbEnhancedClient.table(tableName, TableSchema.fromImmutableClass(UrlEntity.class));
  }
}
