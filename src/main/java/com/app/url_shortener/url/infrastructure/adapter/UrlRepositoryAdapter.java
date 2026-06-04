package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.PageUrlResult;
import com.app.url_shortener.url.application.result.UrlListItemResult;
import com.app.url_shortener.url.domain.exception.ShortCodeCollisionException;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.cursor.DynamoDbCursorCodec;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import com.app.url_shortener.url.infrastructure.mapper.UrlMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.IgnoreNullsMode;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
@RequiredArgsConstructor
public class UrlRepositoryAdapter implements UrlRepositoryPort {

  private static final String SHORT_CODE_ATTRIBUTE = "shortCode";


  private final UrlMapper urlMapper;
  private final DynamoDbCursorCodec  cursorCodec;
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

    String newStatusGsi = UrlStatus.DELETED.name() + "#" + url.getCreatedAt() + "#" + url.getShortCode();

    var softDeleteEntity =
            UrlEntity.builder()
                    .shortCode(url.getShortCode())
                    .status(UrlStatus.DELETED)
                    .deletedAt(now)
                    .deletedBy(deletedBy)
                    .updatedAt(now)
                    .statusCreatedAtShortCodeGsi(newStatusGsi)
                    .build();

    var expression =
            Expression.builder()
                    .expression("attribute_exists(#pk) AND #status = :active")
                    .expressionNames(Map.of("#pk", SHORT_CODE_ATTRIBUTE, "#status", "status"))
                    .expressionValues(Map.of(":active", toAttributeValue(UrlStatus.ACTIVE.name())))
                    .build();

    var request =
            UpdateItemEnhancedRequest.builder(UrlEntity.class)
                    .item(softDeleteEntity)
                    .ignoreNullsMode(IgnoreNullsMode.SCALAR_ONLY)
                    .conditionExpression(expression)
                    .build();

    try {
      urlTable.updateItem(request);
    } catch (ConditionalCheckFailedException exception) {
      // A leitura consistente do use case já validou existência e permissão. Neste ponto, uma
      // falha condicional significa que outra requisição deletou a URL primeiro.
    }
  }

  @Override
  public PageUrlResult findAllByUserId(UUID userId, int limit, String cursor, UrlStatusFilter statusFilter) {
    String indexName = statusFilter.isAll() ? "user-index" : "user-status-index";
    DynamoDbIndex<UrlEntity> index = urlTable.index(indexName);

    String userIdStr = userId.toString();
    String sortValueStr = statusFilter.name() + "#";
    var conditional = statusFilter.isAll() ?
            QueryConditional.keyEqualTo(key -> key.partitionValue(userIdStr)) :
            QueryConditional.sortBeginsWith(key -> key.partitionValue(userIdStr).sortValue(sortValueStr));

    var requestBuilder =
            QueryEnhancedRequest.builder()
                    .queryConditional(conditional)
                    .scanIndexForward(false) // Ordena os itens do mais recente para o mais antigo
                    .limit(limit); // limite de itens por página

    if (cursor != null && !cursor.isBlank()) {
      requestBuilder.exclusiveStartKey(cursorCodec.decode(cursor));
    }

    var pages = index.query(requestBuilder.build());
    var iterator = pages.iterator();

    if (!iterator.hasNext()) {
      return new PageUrlResult(List.of(), null);
    }

    Page<UrlEntity> page = iterator.next();
    List<UrlListItemResult> urls = page.items().stream()
            .map(urlMapper::toDomain)
            .map(urlMapper::toListItemResult)
            .toList();

    String nextCursor = page.lastEvaluatedKey() != null
            ? cursorCodec.encode(page.lastEvaluatedKey())
            : null;

    return new PageUrlResult(urls, nextCursor);
  }

  private AttributeValue toAttributeValue(String value) {
    return AttributeValue.builder().s(value).build();
  }
}
