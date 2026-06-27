package com.app.url_shortener.url.infrastructure.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.app.url_shortener.url.application.command.UrlStatusFilter;
import com.app.url_shortener.url.application.result.UrlListItemResult;
import com.app.url_shortener.url.application.result.UrlRankingItemResult;
import com.app.url_shortener.url.domain.exception.ShortCodeCollisionException;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.cursor.DynamoDbCursorCodec;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import com.app.url_shortener.url.infrastructure.mapper.UrlMapper;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador de Repositório de URL")
@SuppressWarnings({"unchecked", "rawtypes"})
class UrlRepositoryAdapterTest {

  private static final UUID USER_ID = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac001");

  @Mock private DynamoDbTable<UrlEntity> urlTable;

  @Mock private DynamoDbClient dynamoDbClient;

  @Mock private UrlMapper urlMapper;

  @Mock private DynamoDbCursorCodec cursorCodec;

  @Mock private DynamoDbIndex<UrlEntity> userIndex;

  @Mock private PageIterable<UrlEntity> pageIterable;

  @Mock private Page<UrlEntity> page;

  @Mock private Page<UrlEntity> secondPage;

  @InjectMocks private UrlRepositoryAdapter adapter;

  @Nested
  @DisplayName("Persistência")
  class SaveTests {

    @Test
    @DisplayName(
        "Deve mapear domínio e salvar entidade com condição contra colisão de código curto")
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
      assertThat(request.conditionExpression().expressionNames()).containsEntry("#pk", "shortCode");
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
      assertThatThrownBy(() -> adapter.save(url)).isInstanceOf(ShortCodeCollisionException.class);
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
      when(urlTable.tableName()).thenReturn("url");

      // 2. Act
      adapter.softDeleteByShortCode(url, deletedBy);

      // 3. Assert
      var requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
      verify(dynamoDbClient).updateItem(requestCaptor.capture());

      var request = requestCaptor.getValue();
      assertThat(request.tableName()).isEqualTo("url");
      assertThat(request.key())
          .isEqualTo(Map.of("shortCode", AttributeValue.builder().s(shortCode).build()));
      assertThat(request.updateExpression())
          .contains(
              "#status = :deleted",
              "#deletedAt = :deletedAt",
              "#deletedBy = :deletedBy",
              "#updatedAt = :updatedAt",
              "#statusGsi = :statusGsi",
              "REMOVE #activeRankingUserIdGsi")
          .doesNotContain("accessCount", "lastAccessedAt");
      assertThat(request.conditionExpression())
          .isEqualTo("attribute_exists(#pk) AND #status = :active");
      assertThat(request.expressionAttributeNames())
          .containsEntry("#pk", "shortCode")
          .containsEntry("#status", "status")
          .containsEntry("#deletedAt", "deletedAt")
          .containsEntry("#deletedBy", "deletedBy")
          .containsEntry("#updatedAt", "updatedAt")
          .containsEntry("#statusGsi", "statusCreatedAtShortCodeGsi")
          .containsEntry("#activeRankingUserIdGsi", "activeRankingUserIdGsi")
          .doesNotContainValue("accessCount")
          .doesNotContainValue("lastAccessedAt");
      assertThat(request.expressionAttributeValues())
          .containsEntry(":active", AttributeValue.builder().s(UrlStatus.ACTIVE.name()).build())
          .containsEntry(":deleted", AttributeValue.builder().s(UrlStatus.DELETED.name()).build())
          .containsEntry(":deletedBy", AttributeValue.builder().s(deletedBy.toString()).build())
          .containsEntry(
              ":statusGsi",
              AttributeValue.builder().s("DELETED#2026-05-07T10:00:00Z#aB3dE").build());
      assertThat(request.expressionAttributeValues().get(":deletedAt"))
          .isEqualTo(request.expressionAttributeValues().get(":updatedAt"));
      assertThat(request.expressionAttributeValues().get(":deletedAt").s()).isNotBlank();
      verify(urlTable).tableName();
      verifyNoMoreInteractions(dynamoDbClient, urlTable, urlMapper);
    }

    @Test
    @DisplayName("Deve ignorar falha condicional quando outra requisição já deletou a URL")
    void shouldIgnoreConditionalFailureWhenAnotherRequestAlreadyDeletedUrl() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var deletedBy = UUID.fromString("019a16f1-ae7f-7c9d-9e18-44773f1ac002");
      var url = activeUrl(shortCode, "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var exception = ConditionalCheckFailedException.builder().message("already deleted").build();
      when(urlTable.tableName()).thenReturn("url");
      doThrow(exception).when(dynamoDbClient).updateItem(any(UpdateItemRequest.class));

      // 2. Act
      adapter.softDeleteByShortCode(url, deletedBy);

      // 3. Assert
      verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
      verify(urlTable).tableName();
      verifyNoMoreInteractions(dynamoDbClient, urlTable, urlMapper);
    }
  }

  @Nested
  @DisplayName("Incremento da contagem de acessos")
  class IncrementAccessCountTests {

    @Test
    @DisplayName("Deve incrementar contador atomicamente e atualizar timestamp mais recente")
    void shouldAtomicallyIncrementCounterAndUpdateLatestTimestamp() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var delta = 3L;
      var lastAccessedAt = Instant.parse("2026-06-06T12:30:45.123456Z");
      when(urlTable.tableName()).thenReturn("url");

      // 2. Act
      adapter.incrementAccessCount(shortCode, delta, lastAccessedAt);

      // 3. Assert
      var requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
      verify(dynamoDbClient).updateItem(requestCaptor.capture());
      assertThatCounterAndTimestampRequest(
          requestCaptor.getValue(), shortCode, delta, lastAccessedAt);
      verify(urlTable).tableName();
      verifyNoMoreInteractions(dynamoDbClient, urlTable);
    }

    @Test
    @DisplayName("Deve incrementar somente contador quando já existir timestamp mais recente")
    void shouldIncrementOnlyCounterWhenStoredTimestampIsNewer() {
      // 1. Arrange
      var shortCode = "aB3dE";
      var delta = 2L;
      var lastAccessedAt = Instant.parse("2026-06-06T12:30:45Z");
      var conditionalFailure =
          ConditionalCheckFailedException.builder().message("newer timestamp").build();
      when(urlTable.tableName()).thenReturn("url");
      when(dynamoDbClient.updateItem(any(UpdateItemRequest.class)))
          .thenThrow(conditionalFailure)
          .thenReturn(null);

      // 2. Act
      adapter.incrementAccessCount(shortCode, delta, lastAccessedAt);

      // 3. Assert
      var requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
      verify(dynamoDbClient, times(2)).updateItem(requestCaptor.capture());
      assertThatCounterAndTimestampRequest(
          requestCaptor.getAllValues().get(0), shortCode, delta, lastAccessedAt);
      assertThatCounterOnlyRequest(
          requestCaptor.getAllValues().get(1), shortCode, delta, lastAccessedAt);
      verify(urlTable, times(2)).tableName();
      verifyNoMoreInteractions(dynamoDbClient, urlTable);
    }

    @Test
    @DisplayName("Deve propagar falha condicional quando URL não existir")
    void shouldPropagateConditionalFailureWhenUrlDoesNotExist() {
      // 1. Arrange
      var conditionalFailure =
          ConditionalCheckFailedException.builder().message("missing url").build();
      when(urlTable.tableName()).thenReturn("url");
      when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(conditionalFailure);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  adapter.incrementAccessCount(
                      "missing", 1L, Instant.parse("2026-06-06T12:30:45Z")))
          .isSameAs(conditionalFailure);
      verify(dynamoDbClient, times(2)).updateItem(any(UpdateItemRequest.class));
      verify(urlTable, times(2)).tableName();
      verifyNoMoreInteractions(dynamoDbClient, urlTable);
    }

    @Test
    @DisplayName("Deve propagar falha DynamoDB sem tentar atualização alternativa")
    void shouldPropagateDynamoDbFailureWithoutFallbackUpdate() {
      // 1. Arrange
      var exception = DynamoDbException.builder().message("unavailable").build();
      when(urlTable.tableName()).thenReturn("url");
      when(dynamoDbClient.updateItem(any(UpdateItemRequest.class))).thenThrow(exception);

      // 2. Act & 3. Assert
      assertThatThrownBy(
              () ->
                  adapter.incrementAccessCount("aB3dE", 1L, Instant.parse("2026-06-06T12:30:45Z")))
          .isSameAs(exception);
      verify(dynamoDbClient).updateItem(any(UpdateItemRequest.class));
      verify(urlTable).tableName();
      verifyNoMoreInteractions(dynamoDbClient, urlTable);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    @DisplayName("Deve rejeitar código curto ausente ou em branco")
    void shouldRejectMissingOrBlankShortCode(String shortCode) {
      // 1. Arrange
      var lastAccessedAt = Instant.parse("2026-06-06T12:30:45Z");

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.incrementAccessCount(shortCode, 1L, lastAccessedAt))
          .isInstanceOf(IllegalArgumentException.class);
      verifyNoInteractions(dynamoDbClient, urlTable);
    }

    @ParameterizedTest
    @ValueSource(longs = {0L, -1L})
    @DisplayName("Deve rejeitar incremento menor ou igual a zero")
    void shouldRejectNonPositiveDelta(long delta) {
      // 1. Arrange
      var lastAccessedAt = Instant.parse("2026-06-06T12:30:45Z");

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.incrementAccessCount("aB3dE", delta, lastAccessedAt))
          .isInstanceOf(IllegalArgumentException.class);
      verifyNoInteractions(dynamoDbClient, urlTable);
    }

    @Test
    @DisplayName("Deve rejeitar timestamp ausente")
    void shouldRejectMissingLastAccessedAt() {
      // 1. Arrange

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.incrementAccessCount("aB3dE", 1L, null))
          .isInstanceOf(NullPointerException.class);
      verifyNoInteractions(dynamoDbClient, urlTable);
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
      var firstUrl =
          activeUrl("aB3dE", "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var secondUrl =
          activeUrl("fG4hI", "https://spring.io", Instant.parse("2026-05-08T11:30:00Z"));
      var firstListItem =
          new UrlListItemResult(
              "https://google.com",
              "aB3dE",
              Instant.parse("2026-05-07T10:00:00Z"),
              UrlStatus.ACTIVE);
      var secondListItem =
          new UrlListItemResult(
              "https://spring.io",
              "fG4hI",
              Instant.parse("2026-05-08T11:30:00Z"),
              UrlStatus.ACTIVE);
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
          .isEqualTo(
              QueryConditional.sortBeginsWith(
                  key -> key.partitionValue(USER_ID.toString()).sortValue("ACTIVE#")));
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
          .isEqualTo(
              QueryConditional.sortBeginsWith(
                  key -> key.partitionValue(USER_ID.toString()).sortValue("DELETED#")));
      verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper, cursorCodec);
    }

    @Test
    @DisplayName("Deve buscar todas as URLs no índice por usuário")
    void shouldFindAllUrlsUsingUserIndex() {
      // 1. Arrange
      var limit = 10;
      var firstEntity = urlEntity("aB3dE", "https://google.com", "2026-05-07T10:00");
      var secondEntity = urlEntity("fG4hI", "https://spring.io", "2026-05-08T11:30");
      var firstUrl =
          activeUrl("aB3dE", "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var secondUrl =
          activeUrl("fG4hI", "https://spring.io", Instant.parse("2026-05-08T11:30:00Z"));
      var firstListItem =
          new UrlListItemResult(
              "https://google.com",
              "aB3dE",
              Instant.parse("2026-05-07T10:00:00Z"),
              UrlStatus.ACTIVE);
      var secondListItem =
          new UrlListItemResult(
              "https://spring.io",
              "fG4hI",
              Instant.parse("2026-05-08T11:30:00Z"),
              UrlStatus.ACTIVE);
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
    @DisplayName("Deve retornar página vazia quando índice não retornar páginas")
    void shouldReturnEmptyPageWhenIndexDoesNotReturnPages() {
      // 1. Arrange
      var limit = 10;
      when(urlTable.index("user-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.<Page<UrlEntity>>of().iterator());

      // 2. Act
      var result = adapter.findAllByUserId(USER_ID, limit, null, UrlStatusFilter.ALL);

      // 3. Assert
      assertThat(result.urls()).isEmpty();
      assertThat(result.nextCursor()).isNull();

      var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
      verify(urlTable).index("user-index");
      verify(userIndex).query(requestCaptor.capture());
      assertThat(requestCaptor.getValue().limit()).isEqualTo(limit);
      assertThat(requestCaptor.getValue().scanIndexForward()).isFalse();
      assertThat(requestCaptor.getValue().queryConditional())
          .isEqualTo(QueryConditional.keyEqualTo(key -> key.partitionValue(USER_ID.toString())));
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
      assertThat(requestCaptor.getValue().exclusiveStartKey())
          .containsEntry("shortCode", AttributeValue.builder().s("aB3dE").build());
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

  @Nested
  @DisplayName("Ranking por usuário")
  class FindTopAccessedActiveByUserIdTests {

    @Test
    @DisplayName("Deve buscar ranking de URLs ativas no índice por usuário e acessos")
    void shouldFindTopAccessedActiveUrlsUsingUserActiveRankingIndex() {
      // 1. Arrange
      var rankingSize = 10;
      var firstEntity = urlEntity("aB3dE", "https://google.com", "2026-05-07T10:00");
      var secondEntity = urlEntity("fG4hI", "https://spring.io", "2026-05-08T11:30");
      var firstUrl =
          Url.restore(
              USER_ID,
              "aB3dE",
              "https://google.com",
              Instant.parse("2026-05-07T10:00:00Z"),
              UrlStatus.ACTIVE,
              null,
              null,
              Instant.parse("2026-05-07T10:00:00Z"),
              42,
              Instant.parse("2026-06-08T10:00:00Z"));
      var secondUrl =
          Url.restore(
              USER_ID,
              "fG4hI",
              "https://spring.io",
              Instant.parse("2026-05-08T11:30:00Z"),
              UrlStatus.ACTIVE,
              null,
              null,
              Instant.parse("2026-05-08T11:30:00Z"),
              30,
              Instant.parse("2026-06-08T09:00:00Z"));
      var firstRankingItem =
          new UrlRankingItemResult(
              "https://google.com",
              "aB3dE",
              Instant.parse("2026-05-07T10:00:00Z"),
              UrlStatus.ACTIVE,
              42,
              Instant.parse("2026-06-08T10:00:00Z"));
      var secondRankingItem =
          new UrlRankingItemResult(
              "https://spring.io",
              "fG4hI",
              Instant.parse("2026-05-08T11:30:00Z"),
              UrlStatus.ACTIVE,
              30,
              Instant.parse("2026-06-08T09:00:00Z"));

      when(urlTable.index("user-active-ranking-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of(firstEntity, secondEntity));
      when(urlMapper.toDomain(firstEntity)).thenReturn(firstUrl);
      when(urlMapper.toDomain(secondEntity)).thenReturn(secondUrl);
      when(urlMapper.toRankingItemResult(firstUrl)).thenReturn(firstRankingItem);
      when(urlMapper.toRankingItemResult(secondUrl)).thenReturn(secondRankingItem);

      // 2. Act
      var result = adapter.findTopAccessedActiveByUserId(USER_ID, rankingSize);

      // 3. Assert
      assertThat(result.urls()).containsExactly(firstRankingItem, secondRankingItem);

      var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
      verify(urlTable).index("user-active-ranking-index");
      verify(userIndex).query(requestCaptor.capture());
      assertThat(requestCaptor.getValue().limit()).isEqualTo(rankingSize);
      assertThat(requestCaptor.getValue().scanIndexForward()).isFalse();
      assertThat(requestCaptor.getValue().filterExpression()).isNull();
      assertThat(requestCaptor.getValue().exclusiveStartKey()).isNull();
      assertThat(requestCaptor.getValue().queryConditional())
          .isEqualTo(QueryConditional.keyEqualTo(key -> key.partitionValue(USER_ID.toString())));
      verify(urlMapper).toDomain(firstEntity);
      verify(urlMapper).toDomain(secondEntity);
      verify(urlMapper).toRankingItemResult(firstUrl);
      verify(urlMapper).toRankingItemResult(secondUrl);
      verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper, cursorCodec);
    }

    @Test
    @DisplayName("Deve retornar ranking vazio quando índice não retornar páginas")
    void shouldReturnEmptyRankingWhenIndexDoesNotReturnPages() {
      // 1. Arrange
      var rankingSize = 3;
      when(urlTable.index("user-active-ranking-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.<Page<UrlEntity>>of().iterator());

      // 2. Act
      var result = adapter.findTopAccessedActiveByUserId(USER_ID, rankingSize);

      // 3. Assert
      assertThat(result.urls()).isEmpty();

      var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
      verify(urlTable).index("user-active-ranking-index");
      verify(userIndex).query(requestCaptor.capture());
      assertThat(requestCaptor.getValue().limit()).isEqualTo(rankingSize);
      assertThat(requestCaptor.getValue().scanIndexForward()).isFalse();
      assertThat(requestCaptor.getValue().filterExpression()).isNull();
      assertThat(requestCaptor.getValue().queryConditional())
          .isEqualTo(QueryConditional.keyEqualTo(key -> key.partitionValue(USER_ID.toString())));
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
    return Url.restore(
        USER_ID,
        shortCode,
        originalUrl,
        createdAt,
        UrlStatus.ACTIVE,
        null,
        null,
        createdAt,
        0,
        null);
  }

  private Consumer<GetItemEnhancedRequest.Builder> anyGetItemRequestConsumer() {
    return any(Consumer.class);
  }

  private ArgumentCaptor<Consumer<GetItemEnhancedRequest.Builder>> getItemRequestConsumerCaptor() {
    return ArgumentCaptor.forClass((Class) Consumer.class);
  }

  private void assertThatGetItemRequestUsesShortCode(
      Consumer<GetItemEnhancedRequest.Builder> requestConsumer, String shortCode) {
    var requestBuilder = GetItemEnhancedRequest.builder();
    requestConsumer.accept(requestBuilder);
    var request = requestBuilder.build();

    assertThat(request.key().partitionKeyValue().s()).isEqualTo(shortCode);
    assertThat(request.consistentRead()).isTrue();
  }

  private void assertThatCounterAndTimestampRequest(
      UpdateItemRequest request, String shortCode, long delta, Instant lastAccessedAt) {
    assertThat(request.tableName()).isEqualTo("url");
    assertThat(request.key())
        .containsEntry("shortCode", AttributeValue.builder().s(shortCode).build());
    assertThat(request.updateExpression())
        .isEqualTo("SET #lastAccessedAt = :lastAccessedAt ADD #accessCount :delta");
    assertThat(request.conditionExpression())
        .isEqualTo(
            "attribute_exists(#pk) AND (attribute_not_exists(#lastAccessedAt) OR #lastAccessedAt <= :lastAccessedAt)");
    assertThatCounterExpressionAttributes(request, delta, lastAccessedAt);
  }

  private void assertThatCounterOnlyRequest(
      UpdateItemRequest request, String shortCode, long delta, Instant lastAccessedAt) {
    assertThat(request.tableName()).isEqualTo("url");
    assertThat(request.key())
        .containsEntry("shortCode", AttributeValue.builder().s(shortCode).build());
    assertThat(request.updateExpression()).isEqualTo("ADD #accessCount :delta");
    assertThat(request.conditionExpression())
        .isEqualTo("attribute_exists(#pk) AND #lastAccessedAt > :lastAccessedAt");
    assertThatCounterExpressionAttributes(request, delta, lastAccessedAt);
  }

  private void assertThatCounterExpressionAttributes(
      UpdateItemRequest request, long delta, Instant lastAccessedAt) {
    assertThat(request.expressionAttributeNames())
        .containsEntry("#pk", "shortCode")
        .containsEntry("#accessCount", "accessCount")
        .containsEntry("#lastAccessedAt", "lastAccessedAt");
    assertThat(request.expressionAttributeValues())
        .containsEntry(":delta", AttributeValue.builder().n(Long.toString(delta)).build())
        .containsEntry(
            ":lastAccessedAt",
            AttributeValue.builder().s(formatSortableInstant(lastAccessedAt)).build());
  }

  private String formatSortableInstant(Instant instant) {
    var value = instant.toString();
    var separatorIndex = value.indexOf('.');
    if (separatorIndex < 0) {
      return value.replace("Z", ".000000000Z");
    }

    var fractionalDigits = value.length() - separatorIndex - 2;
    return value.replace("Z", "0".repeat(9 - fractionalDigits) + "Z");
  }
}
