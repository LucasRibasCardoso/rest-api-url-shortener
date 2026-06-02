package com.app.url_shortener.url.infrastructure.entity;

import lombok.Builder;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.util.UUID;

@Getter
@Builder
@DynamoDbImmutable(builder = UrlEntity.UrlEntityBuilder.class)
public class UrlEntity {

  private final UUID userId;
  private final String shortCode;
  private final String originalUrl;
  private final String createdAt;
  private final String updatedAt;
  private final String status;
  private final String deletedAt;
  private final UUID deletedBy;

  @DynamoDbPartitionKey
  public String getShortCode() {
    return shortCode;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = "user-index")
  public UUID getUserId() {
    return userId;
  }

}
