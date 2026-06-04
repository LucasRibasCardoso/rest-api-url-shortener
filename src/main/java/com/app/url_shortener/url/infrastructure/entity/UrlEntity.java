package com.app.url_shortener.url.infrastructure.entity;

import com.app.url_shortener.url.domain.model.UrlStatus;
import java.time.Instant;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbImmutable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;

@Getter
@Builder
@DynamoDbImmutable(builder = UrlEntity.UrlEntityBuilder.class)
public class UrlEntity {

  private final UUID userId;
  private final String shortCode;
  private final String originalUrl;
  private final Instant createdAt;
  private final Instant updatedAt;
  private final UrlStatus status;
  private final Instant deletedAt;
  private final UUID deletedBy;

  private final String createdAtShortCodeGsi;
  private final String statusCreatedAtShortCodeGsi;

  @DynamoDbPartitionKey
  public String getShortCode() {
    return shortCode;
  }

  @DynamoDbSecondaryPartitionKey(indexNames = {"user-index", "user-status-index"})
  public UUID getUserId() {
    return userId;
  }

  @DynamoDbSecondarySortKey(indexNames = "user-index")
  public String getCreatedAtShortCodeGsi() {
    return createdAtShortCodeGsi;
  }

  @DynamoDbSecondarySortKey(indexNames = "user-status-index")
  public String getStatusCreatedAtShortCodeGsi() {
    return statusCreatedAtShortCodeGsi;
  }
}
