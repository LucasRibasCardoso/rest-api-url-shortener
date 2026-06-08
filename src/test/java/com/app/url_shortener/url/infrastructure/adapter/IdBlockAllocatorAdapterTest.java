package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.domain.exception.CounterIdAllocationException;
import com.app.url_shortener.url.domain.exception.UrlErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.DynamoDbException;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughputExceededException;
import software.amazon.awssdk.services.dynamodb.model.ResourceNotFoundException;
import software.amazon.awssdk.services.dynamodb.model.ReturnValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemResponse;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
@DisplayName("Testes de Unidade - Adaptador DynamoDB de Contador de IDs")
class IdBlockAllocatorAdapterTest {

  private static final String COUNTER_TABLE_NAME = "url-counters";
  private static final String COUNTER_NAME = "url-id";

  @Mock
  private DynamoDbClient dynamoDbClient;

  private IdBlockAllocatorAdapter adapter;

  @BeforeEach
  void setUp() {
    adapter = new IdBlockAllocatorAdapter(dynamoDbClient, COUNTER_TABLE_NAME, COUNTER_NAME);
  }

  @Nested
  @DisplayName("Alocação de bloco")
  class AllocateBlockTests {

    @Test
    @DisplayName("Deve atualizar contador no DynamoDB e retornar primeiro ID do bloco")
    void shouldUpdateDynamoDbCounterAndReturnFirstIdOfBlock() {
      // 1. Arrange
      var blockSize = 100L;
      var response =
          UpdateItemResponse.builder()
              .attributes(Map.of("currentValue", AttributeValue.builder().n("250").build()))
              .build();
      when(dynamoDbClient.updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class)))
          .thenReturn(response);

      // 2. Act
      var result = adapter.allocateBlock(blockSize);

      // 3. Assert
      assertThat(result).isEqualTo(151L);
      var requestCaptor = ArgumentCaptor.forClass(UpdateItemRequest.class);
      verify(dynamoDbClient).updateItem(requestCaptor.capture());
      assertThatUpdateRequestAllocatesBlock(requestCaptor.getValue(), blockSize);
      verifyNoMoreInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("Deve rejeitar tamanho de bloco menor ou igual a zero")
    void shouldRejectBlockSizeLessThanOrEqualToZero() {
      // 1. Arrange
      var blockSize = 0L;

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.allocateBlock(blockSize))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage("Block size must be greater than 0.");
      verifyNoInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("Deve encapsular resposta inválida quando DynamoDB não retornar currentValue")
    void shouldWrapInvalidResponseWhenDynamoDbDoesNotReturnCurrentValue() {
      // 1. Arrange
      var response = UpdateItemResponse.builder().attributes(Map.of()).build();
      when(dynamoDbClient.updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class)))
          .thenReturn(response);

      // 2. Act & 3. Assert
      assertThatThrownBy(() -> adapter.allocateBlock(100L))
          .isInstanceOfSatisfying(
              CounterIdAllocationException.class,
              exception -> {
                assertThat(exception.getErrorCode())
                    .isEqualTo(UrlErrorCode.COUNTER_ID_INVALID_RESPONSE);
                assertThat(exception)
                    .hasMessage(UrlErrorCode.COUNTER_ID_INVALID_RESPONSE.getMessage());
              });
      verify(dynamoDbClient).updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class));
      verifyNoMoreInteractions(dynamoDbClient);
    }

    @Test
    @DisplayName("Deve encapsular falha condicional do DynamoDB")
    void shouldWrapConditionalCheckFailedException() {
      // 1. Arrange
      var cause = ConditionalCheckFailedException.builder().message("missing counter").build();
      when(dynamoDbClient.updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class)))
          .thenThrow(cause);

      // 2. Act & 3. Assert
      assertCounterIdAllocationException(
          cause,
          UrlErrorCode.COUNTER_ID_CONDITIONAL_CHECK_FAILED);
    }

    @Test
    @DisplayName("Deve encapsular estouro de throughput provisionado do DynamoDB")
    void shouldWrapProvisionedThroughputExceededException() {
      // 1. Arrange
      var cause = ProvisionedThroughputExceededException.builder().message("throughput").build();
      when(dynamoDbClient.updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class)))
          .thenThrow(cause);

      // 2. Act & 3. Assert
      assertCounterIdAllocationException(
          cause,
          UrlErrorCode.COUNTER_ID_THROUGHPUT_EXCEEDED);
    }

    @Test
    @DisplayName("Deve encapsular tabela inexistente do DynamoDB")
    void shouldWrapResourceNotFoundException() {
      // 1. Arrange
      var cause = ResourceNotFoundException.builder().message("missing table").build();
      when(dynamoDbClient.updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class)))
          .thenThrow(cause);

      // 2. Act & 3. Assert
      assertCounterIdAllocationException(
          cause,
          UrlErrorCode.COUNTER_ID_TABLE_NOT_FOUND);
    }

    @Test
    @DisplayName("Deve encapsular falha genérica do DynamoDB")
    void shouldWrapDynamoDbException() {
      // 1. Arrange
      var cause = DynamoDbException.builder().message("dynamodb error").build();
      when(dynamoDbClient.updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class)))
          .thenThrow(cause);

      // 2. Act & 3. Assert
      assertCounterIdAllocationException(
          cause,
          UrlErrorCode.COUNTER_ID_DYNAMODB_FAILURE);
    }

    @Test
    @DisplayName("Deve encapsular falha de comunicação com SDK")
    void shouldWrapSdkClientException() {
      // 1. Arrange
      var cause = SdkClientException.builder().message("network error").build();
      when(dynamoDbClient.updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class)))
          .thenThrow(cause);

      // 2. Act & 3. Assert
      assertCounterIdAllocationException(
          cause,
          UrlErrorCode.COUNTER_ID_CLIENT_FAILURE);
    }
  }

  private void assertCounterIdAllocationException(RuntimeException cause, UrlErrorCode errorCode) {
    assertThatThrownBy(() -> adapter.allocateBlock(100L))
        .isInstanceOfSatisfying(
            CounterIdAllocationException.class,
            exception -> {
              assertThat(exception.getErrorCode()).isEqualTo(errorCode);
              assertThat(exception).hasMessage(errorCode.getMessage()).hasCause(cause);
            });
    verify(dynamoDbClient).updateItem(org.mockito.ArgumentMatchers.any(UpdateItemRequest.class));
    verifyNoMoreInteractions(dynamoDbClient);
  }

  private void assertThatUpdateRequestAllocatesBlock(UpdateItemRequest request, long blockSize) {
    assertThat(request.tableName()).isEqualTo(COUNTER_TABLE_NAME);
    assertThat(request.key())
        .containsEntry("counterName", AttributeValue.builder().s(COUNTER_NAME).build());
    assertThat(request.updateExpression()).isEqualTo("ADD #currentValue :blockSize");
    assertThat(request.conditionExpression()).isEqualTo("attribute_exists(#counterName)");
    assertThat(request.expressionAttributeNames())
        .containsEntry("#counterName", "counterName")
        .containsEntry("#currentValue", "currentValue");
    assertThat(request.expressionAttributeValues())
        .containsEntry(":blockSize", AttributeValue.builder().n(Long.toString(blockSize)).build());
    assertThat(request.returnValues()).isEqualTo(ReturnValue.UPDATED_NEW);
  }
}
