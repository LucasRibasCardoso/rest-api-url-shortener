package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.result.UrlListItemResult;
import com.app.url_shortener.url.domain.exception.ShortCodeCollisionException;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import com.app.url_shortener.url.infrastructure.mapper.UrlMapper;
import com.app.url_shortener.url.infrastructure.cursor.DynamoDbCursorCodec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador de Repositório de URL")
@SuppressWarnings({"unchecked", "rawtypes"})
class UrlRepositoryAdapterTest {

  private static final UUID USER_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");

  @Mock
  private DynamoDbTable<UrlEntity> urlTable;

  @Mock
  private UrlMapper urlMapper;

  @Mock
  private DynamoDbCursorCodec cursorCodec;

  @Mock
  private DynamoDbIndex<UrlEntity> userIndex;

  @Mock
  private PageIterable<UrlEntity> pageIterable;

  @Mock
  private Page<UrlEntity> page;

  @Mock
  private Page<UrlEntity> secondPage;

  @InjectMocks
  private UrlRepositoryAdapter adapter;

  @Nested
  @DisplayName("Persistência")
  class SaveTests {

    @Test
    @DisplayName("Deve mapear domínio e salvar entidade com condição contra colisão de código curto")
    void shouldMapDomainAndSaveEntityWithShortCodeCollisionCondition() {
      // 1. Arrange
      var url = activeUrl("aB3dE", "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var entity = urlEntity("aB3dE", "https://google.com", "2026-05-07T10:00");
      when(urlMapper.toEntity(url)).thenReturn(entity);

      // 2. Act
      adapter.save(url);

      // 3. Assert
      var requestCaptor = ArgumentCaptor.forClass(PutItemEnhancedRequest.class);
      verify(urlMapper).toEntity(url);
      verify(urlTable).putItem(requestCaptor.capture());

      var request = (PutItemEnhancedRequest<UrlEntity>) requestCaptor.getValue();
      assertThat(request.item()).isSameAs(entity);
      assertThat(request.conditionExpression().expression()).isEqualTo("attribute_not_exists(#pk)");
      assertThat(request.conditionExpression().expressionNames())
              .containsEntry("#pk", "shortCode");
      verifyNoMoreInteractions(urlMapper, urlTable);
    }

    @Test
    @DisplayName("Deve traduzir falha condicional do DynamoDB para colisão de código curto")
    void shouldTranslateConditionalCheckFailureToShortCodeCollisionException() {
      // 1. Arrange
      var url = activeUrl("aB3dE", "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var entity = urlEntity("aB3dE", "https://google.com", "2026-05-07T10:00");
      var exception = ConditionalCheckFailedException.builder().message("collision").build();
      when(urlMapper.toEntity(url)).thenReturn(entity);
      doThrow(exception).when(urlTable).putItem(any(PutItemEnhancedRequest.class));

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.save(url))
              .isInstanceOf(ShortCodeCollisionException.class);
      verify(urlMapper).toEntity(url);
      verify(urlTable).putItem(any(PutItemEnhancedRequest.class));
      verifyNoMoreInteractions(urlMapper, urlTable);
    }
  }

  @Nested
  @DisplayName("Busca por código curto")
  class FindByShortCodeTests {

    @Test
    @DisplayName("Deve buscar entidade no DynamoDB, mapear e retornar domínio")
    void shouldGetEntityFromDynamoDbMapAndReturnDomain() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var entity = urlEntity(shortCode, "https://google.com", "2026-05-07T10:00");
      var url = activeUrl(shortCode, "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      when(urlTable.getItem(anyGetItemRequestConsumer())).thenReturn(entity);
      when(urlMapper.toDomain(entity)).thenReturn(url);

      // 2. Act
      var result = adapter.findByShortCode(shortCode);

      // 3. Assert
      assertThat(result).containsSame(url);
      var consumerCaptor = getItemRequestConsumerCaptor();
      verify(urlTable).getItem(consumerCaptor.capture());
      assertThatGetItemRequestUsesShortCode(consumerCaptor.getValue(), shortCode);
      verify(urlMapper).toDomain(entity);
      verifyNoMoreInteractions(urlTable, urlMapper);
    }

    @Test
    @DisplayName("Deve retornar vazio quando DynamoDB não encontrar entidade")
    void shouldReturnEmptyWhenDynamoDbDoesNotFindEntity() {
      // 1. Arrange
      var shortCode = "missing";
      when(urlTable.getItem(anyGetItemRequestConsumer())).thenReturn(null);

      // 2. Act
      var result = adapter.findByShortCode(shortCode);

      // 3. Assert
      assertThat(result).isEmpty();
      var consumerCaptor = getItemRequestConsumerCaptor();
      verify(urlTable).getItem(consumerCaptor.capture());
      assertThatGetItemRequestUsesShortCode(consumerCaptor.getValue(), shortCode);
      verifyNoMoreInteractions(urlTable, urlMapper);
    }
  }

  @Nested
  @DisplayName("Soft delete")
  class SoftDeleteTests {

    @Test
    @DisplayName("Deve atualizar somente metadados de exclusão com condição contra concorrência")
    void shouldUpdateOnlyDeleteMetadataWithConcurrencyCondition() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var deletedBy = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var createdAt = Instant.parse("2026-05-07T10:00:00Z");
      var url = activeUrl(shortCode, "https://google.com", createdAt);

      // 2. Act
      adapter.softDeleteByShortCode(url, deletedBy);

      // 3. Assert
      var requestCaptor = ArgumentCaptor.forClass(UpdateItemEnhancedRequest.class);
      verify(urlTable).updateItem(requestCaptor.capture());

      var request = (UpdateItemEnhancedRequest<UrlEntity>) requestCaptor.getValue();
      var item = request.item();
      assertThat(item.getShortCode()).isEqualTo(shortCode);
      assertThat(item.getStatus()).isEqualTo(UrlStatus.DELETED);
      assertThat(item.getDeletedBy()).isEqualTo(deletedBy);
      assertThat(item.getDeletedAt()).isNotNull();
      assertThat(item.getUpdatedAt()).isEqualTo(item.getDeletedAt());
      assertThat(item.getStatusCreatedAtShortCodeGsi()).isEqualTo("DELETED#2026-05-07T10:00:00Z#aB3dE");
      assertThat(item.getUserId()).isNull();
      assertThat(item.getOriginalUrl()).isNull();
      assertThat(item.getCreatedAt()).isNull();
      assertThat(request.ignoreNullsMode()).isEqualTo(IgnoreNullsMode.SCALAR_ONLY);
      assertThat(request.conditionExpression().expression())
          .isEqualTo("attribute_exists(#pk) AND #status = :active");
      assertThat(request.conditionExpression().expressionNames())
          .containsEntry("#pk", "shortCode")
          .containsEntry("#status", "status");
      assertThat(request.conditionExpression().expressionValues())
          .containsEntry(":active", AttributeValue.builder().s(UrlStatus.ACTIVE.name()).build());
      verifyNoMoreInteractions(urlTable, urlMapper);
    }

    @Test
    @DisplayName("Deve ignorar falha condicional quando outra requisição já deletou a URL")
    void shouldIgnoreConditionalFailureWhenAnotherRequestAlreadyDeletedUrl() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var deletedBy = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var url = activeUrl(shortCode, "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var exception = ConditionalCheckFailedException.builder().message("already deleted").build();
      doThrow(exception).when(urlTable).updateItem(any(UpdateItemEnhancedRequest.class));

      // 2. Act
      adapter.softDeleteByShortCode(url, deletedBy);

      // 3. Assert
      verify(urlTable).updateItem(any(UpdateItemEnhancedRequest.class));
      verifyNoMoreInteractions(urlTable, urlMapper);
    }
  }

  @Nested
  @DisplayName("Busca por usuário")
  class FindAllByUserIdTests {

    @Test
    @DisplayName("Deve buscar URLs ativas no índice por usuário e status")
    void shouldFindActiveUrlsUsingUserStatusIndex() {
      // 1. Arrange
      var limit = 10;
      String cursor = null;
      var firstEntity = urlEntity("aB3dE", "https://google.com", "2026-05-07T10:00");
      var secondEntity = urlEntity("fG4hI", "https://spring.io", "2026-05-08T11:30");
      var firstUrl = activeUrl("aB3dE", "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var secondUrl = activeUrl("fG4hI", "https://spring.io", Instant.parse("2026-05-08T11:30:00Z"));
      var firstListItem = new UrlListItemResult("https://google.com", "aB3dE", Instant.parse("2026-05-07T10:00:00Z"), UrlStatus.ACTIVE);
      var secondListItem = new UrlListItemResult("https://spring.io", "fG4hI", Instant.parse("2026-05-08T11:30:00Z"), UrlStatus.ACTIVE);
      when(urlTable.index("user-status-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of(firstEntity, secondEntity));
      when(page.lastEvaluatedKey()).thenReturn(null);
      when(urlMapper.toDomain(firstEntity)).thenReturn(firstUrl);
      when(urlMapper.toDomain(secondEntity)).thenReturn(secondUrl);
      when(urlMapper.toListItemResult(firstUrl)).thenReturn(firstListItem);
      when(urlMapper.toListItemResult(secondUrl)).thenReturn(secondListItem);

      // 2. Act
      var result = adapter.findAllByUserId(USER_ID, limit, cursor, UrlStatusFilter.ACTIVE);

      // 3. Assert
      assertThat(result.urls()).hasSize(2);
      assertThat(result.urls().get(0).originalUrl()).isEqualTo("https://google.com");
      assertThat(result.urls().get(0).shortCode()).isEqualTo("aB3dE");
      assertThat(result.urls().get(0).createdAt()).isEqualTo(Instant.parse("2026-05-07T10:00:00Z"));
      assertThat(result.urls().get(0).status()).isEqualTo(UrlStatus.ACTIVE);
      assertThat(result.urls().get(1).originalUrl()).isEqualTo("https://spring.io");
      assertThat(result.urls().get(1).shortCode()).isEqualTo("fG4hI");
      assertThat(result.urls().get(1).createdAt()).isEqualTo(Instant.parse("2026-05-08T11:30:00Z"));
      assertThat(result.urls().get(1).status()).isEqualTo(UrlStatus.ACTIVE);
      assertThat(result.nextCursor()).isNull();

      var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
      verify(urlTable).index("user-status-index");
      verify(userIndex).query(requestCaptor.capture());
      assertThat(requestCaptor.getValue().limit()).isEqualTo(limit);
      assertThat(requestCaptor.getValue().exclusiveStartKey()).isNull();
      assertThat(requestCaptor.getValue().filterExpression()).isNull();
      assertThat(requestCaptor.getValue().queryConditional())
          .isEqualTo(QueryConditional.sortBeginsWith(key -> key
              .partitionValue(USER_ID.toString())
              .sortValue("ACTIVE#")));
      verify(urlMapper).toDomain(firstEntity);
      verify(urlMapper).toDomain(secondEntity);
      verify(urlMapper).toListItemResult(firstUrl);
      verify(urlMapper).toListItemResult(secondUrl);
      verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper, cursorCodec);
    }

    @Test
    @DisplayName("Deve buscar URLs deletadas no índice por usuário e status")
    void shouldFindDeletedUrlsUsingUserStatusIndex() {
      // 1. Arrange
      var limit = 5;
      when(urlTable.index("user-status-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of());
      when(page.lastEvaluatedKey()).thenReturn(null);

      // 2. Act
      var result = adapter.findAllByUserId(USER_ID, limit, null, UrlStatusFilter.DELETED);

      // 3. Assert
      assertThat(result.urls()).isEmpty();
      assertThat(result.nextCursor()).isNull();

      var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
      verify(urlTable).index("user-status-index");
      verify(userIndex).query(requestCaptor.capture());
      assertThat(requestCaptor.getValue().limit()).isEqualTo(limit);
      assertThat(requestCaptor.getValue().filterExpression()).isNull();
      assertThat(requestCaptor.getValue().queryConditional())
          .isEqualTo(QueryConditional.sortBeginsWith(key -> key
              .partitionValue(USER_ID.toString())
              .sortValue("DELETED#")));
      verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper, cursorCodec);
    }

    @Test
    @DisplayName("Deve buscar todas as URLs no índice por usuário")
    void shouldFindAllUrlsUsingUserIndex() {
      // 1. Arrange
      var limit = 10;
      var firstEntity = urlEntity("aB3dE", "https://google.com", "2026-05-07T10:00");
      var secondEntity = urlEntity("fG4hI", "https://spring.io", "2026-05-08T11:30");
      var firstUrl = activeUrl("aB3dE", "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var secondUrl = activeUrl("fG4hI", "https://spring.io", Instant.parse("2026-05-08T11:30:00Z"));
      var firstListItem = new UrlListItemResult("https://google.com", "aB3dE", Instant.parse("2026-05-07T10:00:00Z"), UrlStatus.ACTIVE);
      var secondListItem = new UrlListItemResult("https://spring.io", "fG4hI", Instant.parse("2026-05-08T11:30:00Z"), UrlStatus.ACTIVE);
      when(urlTable.index("user-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of(firstEntity, secondEntity));
      when(page.lastEvaluatedKey()).thenReturn(null);
      when(urlMapper.toDomain(firstEntity)).thenReturn(firstUrl);
      when(urlMapper.toDomain(secondEntity)).thenReturn(secondUrl);
      when(urlMapper.toListItemResult(firstUrl)).thenReturn(firstListItem);
      when(urlMapper.toListItemResult(secondUrl)).thenReturn(secondListItem);

      // 2. Act
      var result = adapter.findAllByUserId(USER_ID, limit, null, UrlStatusFilter.ALL);

      // 3. Assert
      assertThat(result.urls()).hasSize(2);
      assertThat(result.nextCursor()).isNull();

      var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
      verify(urlTable).index("user-index");
      verify(userIndex).query(requestCaptor.capture());
      assertThat(requestCaptor.getValue().limit()).isEqualTo(limit);
      assertThat(requestCaptor.getValue().filterExpression()).isNull();
      assertThat(requestCaptor.getValue().queryConditional())
          .isEqualTo(QueryConditional.keyEqualTo(key -> key.partitionValue(USER_ID.toString())));
      verify(urlMapper).toDomain(firstEntity);
      verify(urlMapper).toDomain(secondEntity);
      verify(urlMapper).toListItemResult(firstUrl);
      verify(urlMapper).toListItemResult(secondUrl);
      verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper, cursorCodec);
    }

    @Test
    @DisplayName("Deve buscar próxima página usando cursor inicial decodificado")
    void shouldFindNextPageUsingDecodedExclusiveStartKey() {
      // 1. Arrange
      var limit = 5;
      var startKey = Map.of("shortCode", AttributeValue.builder().s("aB3dE").build());
      var cursor = "valid-cursor";
      when(urlTable.index("user-status-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of());
      when(page.lastEvaluatedKey()).thenReturn(null);
      when(cursorCodec.decode(cursor)).thenReturn(startKey);

      // 2. Act
      var result = adapter.findAllByUserId(USER_ID, limit, cursor, UrlStatusFilter.ACTIVE);

      // 3. Assert
      assertThat(result.urls()).isEmpty();
      assertThat(result.nextCursor()).isNull();

      var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
      verify(urlTable).index("user-status-index");
      verify(userIndex).query(requestCaptor.capture());
      assertThat(requestCaptor.getValue().limit()).isEqualTo(limit);
      assertThat(requestCaptor.getValue().exclusiveStartKey()).containsEntry(
              "shortCode",
              AttributeValue.builder().s("aB3dE").build());
      verify(cursorCodec).decode(cursor);
      verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper, cursorCodec);
    }

    @Test
    @DisplayName("Deve gerar próximo cursor quando índice retornar última chave avaliada")
    void shouldGenerateNextCursorWhenIndexReturnsLastEvaluatedKey() {
      // 1. Arrange
      var limit = 5;
      var lastEvaluatedKey = Map.of("shortCode", AttributeValue.builder().s("aB3dE").build());
      var nextCursor = "next-cursor";
      when(urlTable.index("user-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of());
      when(page.lastEvaluatedKey()).thenReturn(lastEvaluatedKey);
      when(cursorCodec.encode(lastEvaluatedKey)).thenReturn(nextCursor);

      // 2. Act
      var result = adapter.findAllByUserId(USER_ID, limit, " ", UrlStatusFilter.ALL);

      // 3. Assert
      assertThat(result.urls()).isEmpty();
      assertThat(result.nextCursor()).isEqualTo(nextCursor);

      verify(urlTable).index("user-index");
      verify(userIndex).query(any(QueryEnhancedRequest.class));
      verify(cursorCodec).encode(lastEvaluatedKey);
      verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper, cursorCodec);
    }
  }

  private UrlEntity urlEntity(String shortCode, String originalUrl, String createdAt) {
    return UrlEntity.builder()
            .shortCode(shortCode)
            .originalUrl(originalUrl)
            .createdAt(Instant.parse(createdAt + ":00Z"))
            .userId(USER_ID)
            .build();
  }

  private Url activeUrl(String shortCode, String originalUrl, Instant createdAt) {
    return Url.restore(USER_ID, shortCode, originalUrl, createdAt, UrlStatus.ACTIVE, null, null, createdAt);
  }

  private Consumer<GetItemEnhancedRequest.Builder> anyGetItemRequestConsumer() {
    return any(Consumer.class);
  }

  private ArgumentCaptor<Consumer<GetItemEnhancedRequest.Builder>> getItemRequestConsumerCaptor() {
    return ArgumentCaptor.forClass((Class) Consumer.class);
  }

  private void assertThatGetItemRequestUsesShortCode(
          Consumer<GetItemEnhancedRequest.Builder> requestConsumer,
          String shortCode) {
    var requestBuilder = GetItemEnhancedRequest.builder();
    requestConsumer.accept(requestBuilder);
    var request = requestBuilder.build();

    assertThat(request.key().partitionKeyValue().s()).isEqualTo(shortCode);
    assertThat(request.consistentRead()).isTrue();
  }
}
