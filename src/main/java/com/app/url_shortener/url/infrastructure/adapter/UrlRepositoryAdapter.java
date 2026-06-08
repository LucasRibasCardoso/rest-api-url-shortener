package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.UrlPageResult;
import com.app.url_shortener.url.application.result.UrlListItemResult;
import com.app.url_shortener.url.application.result.UrlRankingItemResult;
import com.app.url_shortener.url.application.result.UrlRankingResult;
import com.app.url_shortener.url.domain.exception.ShortCodeCollisionException;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.cursor.DynamoDbCursorCodec;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import com.app.url_shortener.url.infrastructure.mapper.UrlMapper;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
@RequiredArgsConstructor
public class UrlRepositoryAdapter implements UrlRepositoryPort {

  private static final String SHORT_CODE_ATTRIBUTE = "shortCode";
  private static final String STATUS_ATTRIBUTE = "status";
  private static final String DELETED_AT_ATTRIBUTE = "deletedAt";
  private static final String DELETED_BY_ATTRIBUTE = "deletedBy";
  private static final String UPDATED_AT_ATTRIBUTE = "updatedAt";
  private static final String STATUS_CREATED_AT_SHORT_CODE_GSI_ATTRIBUTE = "statusCreatedAtShortCodeGsi";
  private static final String ACTIVE_RANKING_USER_ID_GSI_ATTRIBUTE = "activeRankingUserIdGsi";
  private static final String ACCESS_COUNT_ATTRIBUTE = "accessCount";
  private static final String LAST_ACCESSED_AT_ATTRIBUTE = "lastAccessedAt";
  private static final DateTimeFormatter SORTABLE_INSTANT_FORMATTER = new DateTimeFormatterBuilder().appendInstant(9).toFormatter();

  private final UrlMapper urlMapper;
  private final DynamoDbClient dynamoDbClient;
  private final DynamoDbCursorCodec cursorCodec;
  private final DynamoDbTable<UrlEntity> urlTable;

  @Override
  public void save(Url url) {
    var expression =
        Expression.builder()
            .expression("attribute_not_exists(#pk)")
            .putExpressionName("#pk", SHORT_CODE_ATTRIBUTE)
            .build();

    var entity = urlMapper.toEntity(url);
    var request =
        PutItemEnhancedRequest.builder(UrlEntity.class)
            .item(entity)
            .conditionExpression(expression)
            .build();

    try {
      urlTable.putItem(request);
    } catch (ConditionalCheckFailedException exception) {
      throw new ShortCodeCollisionException();
    }
  }

  @Override
  public Optional<Url> findByShortCode(String shortCode) {
    var key = Key.builder().partitionValue(shortCode).build();
    UrlEntity entity = urlTable.getItem(r -> r.key(key).consistentRead(true));
    return Optional.ofNullable(entity).map(urlMapper::toDomain);
  }

  @Override
  public void softDeleteByShortCode(Url url, UUID deletedBy) {
    Instant now = Instant.now();
    String statusGsi = UrlStatus.DELETED.name() + "#" + url.getCreatedAt() + "#" + url.getShortCode();

    var request =
        UpdateItemRequest.builder()
            .tableName(urlTable.tableName())
            .key(urlPrimaryKey(url.getShortCode()))
            .updateExpression(
                """
                SET #status = :deleted,
                  #deletedAt = :deletedAt,
                  #deletedBy = :deletedBy,
                  #updatedAt = :updatedAt,
                  #statusGsi = :statusGsi
                REMOVE #activeRankingUserIdGsi
                """)
            .conditionExpression("attribute_exists(#pk) AND #status = :active")
            .expressionAttributeNames(
                Map.of(
                    "#pk", SHORT_CODE_ATTRIBUTE,
                    "#status", STATUS_ATTRIBUTE,
                    "#deletedAt", DELETED_AT_ATTRIBUTE,
                    "#deletedBy", DELETED_BY_ATTRIBUTE,
                    "#updatedAt", UPDATED_AT_ATTRIBUTE,
                    "#statusGsi", STATUS_CREATED_AT_SHORT_CODE_GSI_ATTRIBUTE,
                    "#activeRankingUserIdGsi", ACTIVE_RANKING_USER_ID_GSI_ATTRIBUTE))
            .expressionAttributeValues(
                Map.of(
                    ":active", toAttributeValue(UrlStatus.ACTIVE.name()),
                    ":deleted", toAttributeValue(UrlStatus.DELETED.name()),
                    ":deletedAt", toAttributeValue(now.toString()),
                    ":deletedBy", toAttributeValue(deletedBy.toString()),
                    ":updatedAt", toAttributeValue(now.toString()),
                    ":statusGsi", toAttributeValue(statusGsi)))
            .build();

    try {
      dynamoDbClient.updateItem(request);
    } catch (ConditionalCheckFailedException exception) {
      // Url já está deletada ou não existe, então não é necessário fazer nada.
    }
  }

  @Override
  public void incrementAccessCount(String shortCode, long delta, Instant lastAccessedAt) {
    if (shortCode == null || shortCode.isBlank()) {
      throw new IllegalArgumentException("shortCode must not be blank");
    }
    if (delta <= 0) {
      throw new IllegalArgumentException("delta must be greater than 0");
    }
    Objects.requireNonNull(lastAccessedAt, "lastAccessedAt must not be null");

    try {
      dynamoDbClient.updateItem(incrementCounterAndUpdateLastAccessedAt(shortCode, delta, lastAccessedAt));
    } catch (ConditionalCheckFailedException exception) {
      // Preserva um timestamp mais recente, mas ainda contabiliza o acesso recebido fora de ordem.
      dynamoDbClient.updateItem(incrementCounterWhenLastAccessedAtIsNewer(shortCode, delta, lastAccessedAt));
    }
  }

  @Override
  public UrlRankingResult findTopAccessedActiveByUserId(UUID userId, int rankingSize) {
    DynamoDbIndex<UrlEntity> index = urlTable.index("user-active-ranking-index");

    var request = QueryEnhancedRequest.builder()
            .queryConditional(QueryConditional.keyEqualTo(key -> key.partitionValue(userId.toString())))
            .scanIndexForward(false)
            .limit(rankingSize)
            .build();

    var pages = index.query(request);
    var iterator = pages.iterator();

    if (!iterator.hasNext()) {
      return new UrlRankingResult(List.of());
    }

    Page<UrlEntity> page = iterator.next();
    List<UrlRankingItemResult> urls = page.items().stream()
            .map(urlMapper::toDomain)
            .map(urlMapper::toRankingItemResult)
            .toList();

    return new UrlRankingResult(urls);
  }

  @Override
  public UrlPageResult findAllByUserId(UUID userId, int limit, String cursor, UrlStatusFilter statusFilter) {

    String indexName = statusFilter.isAll() ? "user-index" : "user-status-index";
    DynamoDbIndex<UrlEntity> index = urlTable.index(indexName);

    String userIdStr = userId.toString();
    String sortValueStr = statusFilter.name() + "#";
    var conditional =
        statusFilter.isAll()
            ? QueryConditional.keyEqualTo(key -> key.partitionValue(userIdStr))
            : QueryConditional.sortBeginsWith(key -> key.partitionValue(userIdStr).sortValue(sortValueStr));

    var requestBuilder =
        QueryEnhancedRequest.builder()
            .queryConditional(conditional)
            .scanIndexForward(false)
            .limit(limit);

    if (cursor != null && !cursor.isBlank()) {
      requestBuilder.exclusiveStartKey(cursorCodec.decode(cursor));
    }

    var pages = index.query(requestBuilder.build());
    var iterator = pages.iterator();

    if (!iterator.hasNext()) {
      return new UrlPageResult(List.of(), null);
    }

    Page<UrlEntity> page = iterator.next();
    List<UrlListItemResult> urls = page.items().stream()
            .map(urlMapper::toDomain)
            .map(urlMapper::toListItemResult)
            .toList();

    String nextCursor = page.lastEvaluatedKey() != null ? cursorCodec.encode(page.lastEvaluatedKey()) : null;

    return new UrlPageResult(urls, nextCursor);
  }

  private UpdateItemRequest incrementCounterAndUpdateLastAccessedAt(
      String shortCode,
      long delta,
      Instant lastAccessedAt) {

    return UpdateItemRequest.builder()
        .tableName(urlTable.tableName())
        .key(urlPrimaryKey(shortCode))
        .updateExpression("SET #lastAccessedAt = :lastAccessedAt ADD #accessCount :delta")
        .conditionExpression("attribute_exists(#pk) AND (attribute_not_exists(#lastAccessedAt) OR #lastAccessedAt <= :lastAccessedAt)")
        .expressionAttributeNames(
            Map.of(
                "#pk", SHORT_CODE_ATTRIBUTE,
                "#accessCount", ACCESS_COUNT_ATTRIBUTE,
                "#lastAccessedAt", LAST_ACCESSED_AT_ATTRIBUTE))
        .expressionAttributeValues(
            Map.of(
                ":delta", toNumberAttributeValue(delta),
                ":lastAccessedAt", toAttributeValue(SORTABLE_INSTANT_FORMATTER.format(lastAccessedAt))))
        .build();
  }

  private UpdateItemRequest incrementCounterWhenLastAccessedAtIsNewer(
          String shortCode,
          long delta,
          Instant lastAccessedAt) {

    return UpdateItemRequest.builder()
        .tableName(urlTable.tableName())
        .key(urlPrimaryKey(shortCode))
        .updateExpression("ADD #accessCount :delta")
        .conditionExpression("attribute_exists(#pk) AND #lastAccessedAt > :lastAccessedAt")
        .expressionAttributeNames(
            Map.of(
                "#pk", SHORT_CODE_ATTRIBUTE,
                "#accessCount", ACCESS_COUNT_ATTRIBUTE,
                "#lastAccessedAt", LAST_ACCESSED_AT_ATTRIBUTE))
        .expressionAttributeValues(
            Map.of(
                ":delta", toNumberAttributeValue(delta),
                ":lastAccessedAt", toAttributeValue(SORTABLE_INSTANT_FORMATTER.format(lastAccessedAt))))
        .build();
  }

  private Map<String, AttributeValue> urlPrimaryKey(String shortCode) {
    return Map.of(SHORT_CODE_ATTRIBUTE, toAttributeValue(shortCode));
  }

  private AttributeValue toAttributeValue(String value) {
    return AttributeValue.builder().s(value).build();
  }

  private AttributeValue toNumberAttributeValue(long value) {
    return AttributeValue.builder().n(Long.toString(value)).build();
  }
}
