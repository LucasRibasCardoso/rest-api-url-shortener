package com.app.url_shortener.url.infrastructure.adapter;

import com.app.url_shortener.url.application.port.output.CounterIdRepository;
import com.app.url_shortener.url.domain.exception.CounterIdAllocationException;
import com.app.url_shortener.url.domain.exception.UrlErrorCode;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

@Repository
public class DynamoDbCounterIdAdapter implements CounterIdRepository {

  private static final String COUNTER_NAME_ATTRIBUTE = "counterName";
  private static final String CURRENT_VALUE_ATTRIBUTE = "currentValue";

  private final DynamoDbClient dynamoDbClient;
  private final String counterTableName;
  private final String counterName;

  public DynamoDbCounterIdAdapter(
      DynamoDbClient dynamoDbClient,
      @Value("${aws.dynamodb.tables.url-counter}") String counterTableName,
      @Value("${app.id-generator.counter-name}") String counterName) {
    this.dynamoDbClient = dynamoDbClient;
    this.counterTableName = counterTableName;
    this.counterName = counterName;
  }

  @Override
  public Long allocateBlock(long blockSize) {
    if (blockSize <= 0) {
      throw new IllegalArgumentException("Block size must be greater than 0.");
    }

    try {
      UpdateItemRequest request =
          UpdateItemRequest.builder()
              .tableName(counterTableName)
              .key(counterPrimaryKey())
              .updateExpression("ADD #currentValue :blockSize")
              .conditionExpression("attribute_exists(#counterName)")
              .expressionAttributeNames(
                  Map.of(
                      "#counterName", COUNTER_NAME_ATTRIBUTE,
                      "#currentValue", CURRENT_VALUE_ATTRIBUTE))
              .expressionAttributeValues(Map.of(":blockSize", toNumberAttributeValue(blockSize)))
              .returnValues(ReturnValue.UPDATED_NEW)
              .build();

      UpdateItemResponse response = dynamoDbClient.updateItem(request);
      long blockEnd = extractUpdatedCurrentValue(response);
      return calculateBlockStart(blockEnd, blockSize);

    } catch (SdkException exception) {
      throw buildCounterIdAllocationException(exception);
    }
  }

  private Map<String, AttributeValue> counterPrimaryKey() {
    return Map.of(COUNTER_NAME_ATTRIBUTE, toStringAttributeValue(counterName));
  }

  private AttributeValue toNumberAttributeValue(Long blockSize) {
    return AttributeValue.builder().n(blockSize.toString()).build();
  }

  private AttributeValue toStringAttributeValue(String tableName) {
    return AttributeValue.builder().s(tableName).build();
  }

  private CounterIdAllocationException buildCounterIdAllocationException(SdkException exception) {
    if (exception instanceof ConditionalCheckFailedException) {
      return new CounterIdAllocationException(UrlErrorCode.COUNTER_ID_CONDITIONAL_CHECK_FAILED, exception);
    }

    if (exception instanceof ProvisionedThroughputExceededException) {
      return new CounterIdAllocationException(UrlErrorCode.COUNTER_ID_THROUGHPUT_EXCEEDED, exception);
    }

    if (exception instanceof ResourceNotFoundException) {
      return new CounterIdAllocationException(UrlErrorCode.COUNTER_ID_TABLE_NOT_FOUND, exception);
    }

    if (exception instanceof DynamoDbException) {
      return new CounterIdAllocationException(UrlErrorCode.COUNTER_ID_DYNAMODB_FAILURE, exception);
    }

    if (exception instanceof SdkClientException) {
      return new CounterIdAllocationException(UrlErrorCode.COUNTER_ID_CLIENT_FAILURE, exception);
    }

    return new CounterIdAllocationException(UrlErrorCode.COUNTER_ID_CLIENT_FAILURE, exception);
  }

  private long extractUpdatedCurrentValue(UpdateItemResponse response) {
    if (response.attributes() == null
        || !response.attributes().containsKey(CURRENT_VALUE_ATTRIBUTE)) {
      throw new CounterIdAllocationException(UrlErrorCode.COUNTER_ID_INVALID_RESPONSE);
    }

    AttributeValue updatedCounter = response.attributes().get(CURRENT_VALUE_ATTRIBUTE);

    if (updatedCounter == null || updatedCounter.n() == null || updatedCounter.n().isBlank()) {
      throw new IllegalStateException("DynamoDB counter update did not return currentValue");
    }

    try {
      return Long.parseLong(updatedCounter.n());
    } catch (NumberFormatException exception) {
      throw new CounterIdAllocationException(UrlErrorCode.COUNTER_ID_INVALID_RESPONSE);
    }
  }

  private long calculateBlockStart(long blockEnd, long blockSize) {
    long previousCounterValue = Math.subtractExact(blockEnd, blockSize);
    return Math.addExact(previousCounterValue, 1L);
  }
}
