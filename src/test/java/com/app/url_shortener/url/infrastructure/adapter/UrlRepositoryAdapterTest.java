package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.domain.exception.ShortCodeCollisionException;
import com.app.url_shortener.url.domain.model.Url;
import com.app.url_shortener.url.domain.model.UrlStatus;
import com.app.url_shortener.url.infrastructure.entity.UrlEntity;
import com.app.url_shortener.url.infrastructure.mapper.UrlMapper;
import com.app.url_shortener.url.infrastructure.utils.CursorUtil;
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
import java.util.Base64;
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
  private DynamoDbIndex<UrlEntity> userIndex;

  @Mock
  private PageIterable<UrlEntity> pageIterable;

  @Mock
  private Page<UrlEntity> page;

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
  @DisplayName("Exclusão")
  class DeleteTests {

    @Test
    @DisplayName("Deve excluir entidade no DynamoDB usando o código curto como chave de partição")
    void shouldDeleteEntityFromDynamoDbUsingShortCodeAsPartitionKey() {
      // 1. Arrange
      var shortCode = "aB3dE";

      // 2. Act
      adapter.delete(shortCode);

      // 3. Assert
      var consumerCaptor = deleteItemRequestConsumerCaptor();
      verify(urlTable).deleteItem(consumerCaptor.capture());
      assertThatDeleteItemRequestUsesShortCode(consumerCaptor.getValue(), shortCode);
      verifyNoMoreInteractions(urlTable, urlMapper);
    }
  }

  @Nested
  @DisplayName("Busca por usuário")
  class FindAllByUserIdTests {

    @Test
    @DisplayName("Deve buscar primeira página sem cursor inicial e mapear entidades para detalhes de URL")
    void shouldFindFirstPageWithoutExclusiveStartKeyAndMapEntitiesToUrlDetails() {
      // 1. Arrange
      var limit = 10;
      String cursor = null;
      var firstEntity = urlEntity("aB3dE", "https://google.com", "2026-05-07T10:00");
      var secondEntity = urlEntity("fG4hI", "https://spring.io", "2026-05-08T11:30");
      var firstUrl = activeUrl("aB3dE", "https://google.com", Instant.parse("2026-05-07T10:00:00Z"));
      var secondUrl = activeUrl("fG4hI", "https://spring.io", Instant.parse("2026-05-08T11:30:00Z"));
      when(urlTable.index("user-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of(firstEntity, secondEntity));
      when(page.lastEvaluatedKey()).thenReturn(null);
      when(urlMapper.toDomain(firstEntity)).thenReturn(firstUrl);
      when(urlMapper.toDomain(secondEntity)).thenReturn(secondUrl);

      // 2. Act
      var result = adapter.findAllByUserId(USER_ID, limit, cursor);

      // 3. Assert
      assertThat(result.urls()).hasSize(2);
      assertThat(result.urls().get(0).originalUrl()).isEqualTo("https://google.com");
      assertThat(result.urls().get(0).shortCode()).isEqualTo("aB3dE");
      assertThat(result.urls().get(0).createdAt()).isEqualTo(Instant.parse("2026-05-07T10:00:00Z"));
      assertThat(result.urls().get(1).originalUrl()).isEqualTo("https://spring.io");
      assertThat(result.urls().get(1).shortCode()).isEqualTo("fG4hI");
      assertThat(result.urls().get(1).createdAt()).isEqualTo(Instant.parse("2026-05-08T11:30:00Z"));
      assertThat(result.nextCursor()).isNull();

      var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
      verify(urlTable).index("user-index");
      verify(userIndex).query(requestCaptor.capture());
      assertThat(requestCaptor.getValue().limit()).isEqualTo(limit);
      assertThat(requestCaptor.getValue().exclusiveStartKey()).isNull();
      verify(urlMapper).toDomain(firstEntity);
      verify(urlMapper).toDomain(secondEntity);
      verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper);
    }

    @Test
    @DisplayName("Deve buscar próxima página usando cursor inicial decodificado")
    void shouldFindNextPageUsingDecodedExclusiveStartKey() {
      // 1. Arrange
      var limit = 5;
      var startKey = Map.of("shortCode", AttributeValue.builder().s("aB3dE").build());
      var cursor = Base64.getEncoder().encodeToString("valid-cursor".getBytes());
      when(urlTable.index("user-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of());
      when(page.lastEvaluatedKey()).thenReturn(null);

      try (var cursorUtil = mockStatic(CursorUtil.class)) {
        cursorUtil.when(() -> CursorUtil.decode(cursor)).thenReturn(startKey);

        // 2. Act
        var result = adapter.findAllByUserId(USER_ID, limit, cursor);

        // 3. Assert
        assertThat(result.urls()).isEmpty();
        assertThat(result.nextCursor()).isNull();

        var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(urlTable).index("user-index");
        verify(userIndex).query(requestCaptor.capture());
        assertThat(requestCaptor.getValue().limit()).isEqualTo(limit);
        assertThat(requestCaptor.getValue().exclusiveStartKey()).containsEntry(
                "shortCode",
                AttributeValue.builder().s("aB3dE").build());
        cursorUtil.verify(() -> CursorUtil.decode(cursor));
        verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper);
      }
    }

    @Test
    @DisplayName("Deve gerar próximo cursor quando DynamoDB retornar última chave avaliada")
    void shouldGenerateNextCursorWhenDynamoDbReturnsLastEvaluatedKey() {
      // 1. Arrange
      var limit = 10;
      var lastEvaluatedKey = Map.of("shortCode", AttributeValue.builder().s("fG4hI").build());
      var nextCursor = Base64.getEncoder().encodeToString("next-cursor".getBytes());
      when(urlTable.index("user-index")).thenReturn(userIndex);
      when(userIndex.query(any(QueryEnhancedRequest.class))).thenReturn(pageIterable);
      when(pageIterable.iterator()).thenReturn(List.of(page).iterator());
      when(page.items()).thenReturn(List.of());
      when(page.lastEvaluatedKey()).thenReturn(lastEvaluatedKey);

      try (var cursorUtil = mockStatic(CursorUtil.class)) {
        cursorUtil.when(() -> CursorUtil.encode(lastEvaluatedKey)).thenReturn(nextCursor);

        // 2. Act
        var result = adapter.findAllByUserId(USER_ID, limit, " ");

        // 3. Assert
        assertThat(result.urls()).isEmpty();
        assertThat(result.nextCursor()).isEqualTo(nextCursor);

        var requestCaptor = ArgumentCaptor.forClass(QueryEnhancedRequest.class);
        verify(urlTable).index("user-index");
        verify(userIndex).query(requestCaptor.capture());
        assertThat(requestCaptor.getValue().limit()).isEqualTo(limit);
        assertThat(requestCaptor.getValue().exclusiveStartKey()).isNull();
        cursorUtil.verify(() -> CursorUtil.encode(lastEvaluatedKey));
        verifyNoMoreInteractions(urlTable, userIndex, pageIterable, page, urlMapper);
      }
    }
  }

  private UrlEntity urlEntity(String shortCode, String originalUrl, String createdAt) {
    return UrlEntity.builder()
            .shortCode(shortCode)
            .originalUrl(originalUrl)
            .createdAt(createdAt)
            .userId(USER_ID)
            .build();
  }

  private Url activeUrl(String shortCode, String originalUrl, Instant createdAt) {
    return Url.restore(USER_ID, shortCode, originalUrl, createdAt, UrlStatus.ACTIVE, null, null, createdAt);
  }

  private Consumer<GetItemEnhancedRequest.Builder> anyGetItemRequestConsumer() {
    return any(Consumer.class);
  }

  private ArgumentCaptor<Consumer<DeleteItemEnhancedRequest.Builder>> deleteItemRequestConsumerCaptor() {
    return ArgumentCaptor.forClass((Class) Consumer.class);
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
  }

  private void assertThatDeleteItemRequestUsesShortCode(
          Consumer<DeleteItemEnhancedRequest.Builder> requestConsumer,
          String shortCode) {
    var requestBuilder = DeleteItemEnhancedRequest.builder();
    requestConsumer.accept(requestBuilder);
    var request = requestBuilder.build();

    assertThat(request.key().partitionKeyValue().s()).isEqualTo(shortCode);
  }
}
