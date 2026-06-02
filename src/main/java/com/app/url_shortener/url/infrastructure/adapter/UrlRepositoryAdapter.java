package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.port.output.UrlRepositoryPort;
import com.app.url_shortener.url.application.result.PageUrlResult;
import com.app.url_shortener.url.application.result.UrlDetailsResult;
import com.app.url_shortener.url.domain.exception.ShortCodeCollisionException;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import com.app.url_shortener.url.infrastructure.mapper.UrlMapper;
import com.app.url_shortener.url.infrastructure.utils.CursorUtil;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Repository
public class UrlRepositoryAdapter implements UrlRepositoryPort {

  private static final String SHORT_CODE_ATTRIBUTE = "shortCode";

  private final DynamoDbTable<UrlEntity> urlTable;
  private final UrlMapper urlMapper;

  public UrlRepositoryAdapter(DynamoDbTable<UrlEntity> urlTable, UrlMapper urlMapper) {
    this.urlTable = urlTable;
    this.urlMapper = urlMapper;
  }

  @Override
  public void save(Url url) {
    Expression condition =
            Expression.builder()
                    .expression("attribute_not_exists(#pk)")
                    .putExpressionName("#pk", SHORT_CODE_ATTRIBUTE)
                    .build();

    UrlEntity entity = urlMapper.toEntity(url);
    PutItemEnhancedRequest<UrlEntity> request =
            PutItemEnhancedRequest.builder(UrlEntity.class)
                    .item(entity)
                    .conditionExpression(condition)
                    .build();

    try {
      urlTable.putItem(request);
    } catch (ConditionalCheckFailedException exception) {
      throw new ShortCodeCollisionException();
    }
  }

  @Override
  public Optional<Url> findByShortCode(String shortCode) {
    Key key = Key.builder().partitionValue(shortCode).build();
    UrlEntity entity = urlTable.getItem(r -> r.key(key).consistentRead(true));
    return Optional.ofNullable(entity).map(urlMapper::toDomain);
  }

  @Override
  public void delete(String shortCode) {
    Key key = Key.builder()
            .partitionValue(shortCode)
            .build();

    urlTable.deleteItem(r -> r.key(key));
  }

  @Override
  public PageUrlResult findAllByUserId(UUID userId, int limit, String cursor) {
    DynamoDbIndex<UrlEntity> userIndex = urlTable.index("user-index");
    QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
            .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(userId.toString())))
            .limit(limit);

    if (cursor != null && !cursor.isBlank()) {
      requestBuilder.exclusiveStartKey(CursorUtil.decode(cursor));
    }

    Page<UrlEntity> page = userIndex.query(requestBuilder.build()).iterator().next();

    List<UrlDetailsResult> urls = page.items().stream()
            .map(urlMapper::toDomain)
            .map(this::toResult)
            .toList();

    String nextCursor = (page.lastEvaluatedKey() != null)
            ? CursorUtil.encode(page.lastEvaluatedKey())
            : null;

    return new PageUrlResult(urls, nextCursor);
  }

  private UrlDetailsResult toResult(Url url) {
    return new UrlDetailsResult(url.getOriginalUrl(), url.getShortCode(), url.getCreatedAt());
  }
}
