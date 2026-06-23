package com.app.url_shortener.config;

import static org.testcontainers.utility.DockerImageName.parse;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueUrlRequest;
import software.amazon.awssdk.services.sqs.model.PurgeQueueRequest;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueDoesNotExistException;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.VerifyEmailIdentityRequest;

public final class LocalStackContainerSupport {

  private static final DockerImageName LOCALSTACK_IMAGE = parse("localstack/localstack:3.0.0");
  private static final String ACCESS_KEY = "test";
  private static final String SECRET_KEY = "test";
  private static final String URL_TABLE_NAME = "url";
  private static final String COUNTER_TABLE_NAME = "url_counter";
  private static final String URL_COUNTER_NAME = "url_short_code";
  private static final String URL_PRIMARY_KEY = "shortCode";
  private static final String COUNTER_PRIMARY_KEY = "counterName";
  private static final String URL_REDIRECT_EVENTS_QUEUE = "url-redirect-events-queue";
  private static final String URL_REDIRECT_EVENTS_DLQ = "url-redirect-events-dlq";
  private static final String EMAIL_VERIFICATION_EVENTS_QUEUE = "email-verification-events-queue";
  private static final String EMAIL_VERIFICATION_EVENTS_DLQ = "email-verification-events-dlq";
  private static final String SES_FROM_EMAIL = "no-reply@url-shortener.local";
  private static final String MESSAGE_RETENTION_PERIOD = "345600";
  private static final String DLQ_MESSAGE_RETENTION_PERIOD = "604800";
  private static final String RECEIVE_MESSAGE_WAIT_TIME_SECONDS = "20";
  private static final String VISIBILITY_TIMEOUT = "60";
  private static final String MAX_RECEIVE_COUNT = "5";

  private static final LocalStackContainer LOCALSTACK_CONTAINER =
      new LocalStackContainer(LOCALSTACK_IMAGE)
          .withServices(
              LocalStackContainer.Service.DYNAMODB,
              LocalStackContainer.Service.SQS,
              LocalStackContainer.Service.SES);

  static {
    LOCALSTACK_CONTAINER.start();
  }

  private LocalStackContainerSupport() {}

  public static void registerDynamoDbProperties(DynamicPropertyRegistry registry) {
    URI endpoint = LOCALSTACK_CONTAINER.getEndpointOverride(LocalStackContainer.Service.DYNAMODB);

    registry.add("aws.dynamodb.endpoint", endpoint::toString);
    registry.add("aws.dynamodb.region", LOCALSTACK_CONTAINER::getRegion);
    registry.add("aws.dynamodb.access-key", () -> ACCESS_KEY);
    registry.add("aws.dynamodb.secret-key", () -> SECRET_KEY);
    registry.add("aws.dynamodb.tables.url", () -> URL_TABLE_NAME);
    registry.add("aws.dynamodb.tables.url-counter", () -> COUNTER_TABLE_NAME);
  }

  public static void registerSQSProperties(DynamicPropertyRegistry registry) {
    URI endpoint = LOCALSTACK_CONTAINER.getEndpointOverride(LocalStackContainer.Service.SQS);

    registry.add("spring.cloud.aws.sqs.endpoint", endpoint::toString);
    registry.add("spring.cloud.aws.region.static", LOCALSTACK_CONTAINER::getRegion);
    registry.add("spring.cloud.aws.credentials.access-key", () -> ACCESS_KEY);
    registry.add("spring.cloud.aws.credentials.secret-key", () -> SECRET_KEY);
    registry.add("app.aws.sqs.url-redirect-events-queue", () -> URL_REDIRECT_EVENTS_QUEUE);
    registry.add("app.aws.sqs.url-redirect-events-dlq", () -> URL_REDIRECT_EVENTS_DLQ);
    registry.add("app.aws.sqs.email-verification-events-queue", () -> EMAIL_VERIFICATION_EVENTS_QUEUE);
    registry.add("app.aws.sqs.email-verification-events-dlq", () -> EMAIL_VERIFICATION_EVENTS_DLQ);
  }

  public static void registerSesProperties(DynamicPropertyRegistry registry) {
    URI endpoint = sesEndpoint();

    registry.add("app.iam.email-verification.ses.endpoint", endpoint::toString);
    registry.add("app.iam.email-verification.ses.from-email", () -> SES_FROM_EMAIL);
    registry.add("app.iam.email-verification.ses.subject", () -> "Confirme seu e-mail");
    registry.add("app.iam.email-verification.ses.api-call-timeout", () -> "10s");
  }

  public static void setupDynamoDbTables() {
    try (DynamoDbClient dynamoDbClient = createDynamoDbClient()) {
      createUrlTableIfAbsent(dynamoDbClient);
      createCounterTableIfAbsent(dynamoDbClient);
      seedUrlCounterIfAbsent(dynamoDbClient);
    }
  }

  public static void resetDynamoDbTables() {
    try (DynamoDbClient dynamoDbClient = createDynamoDbClient()) {
      clearTable(dynamoDbClient, URL_TABLE_NAME, URL_PRIMARY_KEY);
      clearTable(dynamoDbClient, COUNTER_TABLE_NAME, COUNTER_PRIMARY_KEY);
      seedUrlCounter(dynamoDbClient);
    }
  }

  public static void setupSqsQueues() {
    try (SqsClient sqsClient = createSqsClient()) {
      setupQueuePair(sqsClient, URL_REDIRECT_EVENTS_QUEUE, URL_REDIRECT_EVENTS_DLQ);
      setupQueuePair(sqsClient, EMAIL_VERIFICATION_EVENTS_QUEUE, EMAIL_VERIFICATION_EVENTS_DLQ);
    }
  }

  public static void setupSesIdentity() {
    try (SesClient sesClient = createSesClient()) {
      sesClient.verifyEmailIdentity(
          VerifyEmailIdentityRequest.builder().emailAddress(SES_FROM_EMAIL).build());
    }
  }

  public static void resetSqsQueues() {
    try (SqsClient sqsClient = createSqsClient()) {
      purgeQueue(sqsClient, URL_REDIRECT_EVENTS_QUEUE);
      purgeQueue(sqsClient, URL_REDIRECT_EVENTS_DLQ);
      purgeQueue(sqsClient, EMAIL_VERIFICATION_EVENTS_QUEUE);
      purgeQueue(sqsClient, EMAIL_VERIFICATION_EVENTS_DLQ);
    }
  }

  public static void resetSesMessages() {
    try (var httpClient = HttpClient.newHttpClient()) {
      var request =
          HttpRequest.newBuilder(sesEndpoint().resolve("/_aws/ses"))
              .DELETE()
              .build();
      var response = httpClient.send(request, HttpResponse.BodyHandlers.discarding());

      if (response.statusCode() >= 400) {
        throw new IllegalStateException(
            "Failed to clear LocalStack SES messages. statusCode=" + response.statusCode());
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while clearing LocalStack SES messages", exception);
    } catch (Exception exception) {
      throw new IllegalStateException("Failed to clear LocalStack SES messages", exception);
    }
  }

  public static URI sesEndpoint() {
    return LOCALSTACK_CONTAINER.getEndpointOverride(LocalStackContainer.Service.SES);
  }

  private static void setupQueuePair(SqsClient sqsClient, String queueName, String dlqName) {
    String dlqUrl = createQueueIfAbsent(sqsClient, dlqName, dlqQueueAttributes());
    String dlqArn =
        sqsClient
            .getQueueAttributes(
                GetQueueAttributesRequest.builder()
                    .queueUrl(dlqUrl)
                    .attributeNames(QueueAttributeName.QUEUE_ARN)
                    .build())
            .attributes()
            .get(QueueAttributeName.QUEUE_ARN);

    Map<QueueAttributeName, String> queueAttributes =
        Map.of(
            QueueAttributeName.VISIBILITY_TIMEOUT, VISIBILITY_TIMEOUT,
            QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, RECEIVE_MESSAGE_WAIT_TIME_SECONDS,
            QueueAttributeName.MESSAGE_RETENTION_PERIOD, MESSAGE_RETENTION_PERIOD,
            QueueAttributeName.REDRIVE_POLICY,
            "{\"deadLetterTargetArn\":\""
                + dlqArn
                + "\",\"maxReceiveCount\":\""
                + MAX_RECEIVE_COUNT
                + "\"}");

    createQueueIfAbsent(sqsClient, queueName, queueAttributes);
  }

  static SqsClient createSqsClient() {
    return SqsClient.builder()
        .endpointOverride(LOCALSTACK_CONTAINER.getEndpointOverride(LocalStackContainer.Service.SQS))
        .region(Region.of(LOCALSTACK_CONTAINER.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
        .build();
  }

  private static DynamoDbClient createDynamoDbClient() {
    return DynamoDbClient.builder()
        .endpointOverride(LOCALSTACK_CONTAINER.getEndpointOverride(LocalStackContainer.Service.DYNAMODB))
        .region(Region.of(LOCALSTACK_CONTAINER.getRegion()))
        .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
        .build();
  }

  private static SesClient createSesClient() {
    return SesClient.builder()
        .endpointOverride(sesEndpoint())
        .region(Region.of(LOCALSTACK_CONTAINER.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
        .build();
  }

  private static void createUrlTableIfAbsent(DynamoDbClient dynamoDbClient) {
    if (tableExists(dynamoDbClient, URL_TABLE_NAME)) {
      return;
    }

    dynamoDbClient.createTable(
        CreateTableRequest.builder()
            .tableName(URL_TABLE_NAME)
            .attributeDefinitions(
                attributeDefinition(URL_PRIMARY_KEY, ScalarAttributeType.S),
                attributeDefinition("userId", ScalarAttributeType.S),
                attributeDefinition("createdAtShortCodeGsi", ScalarAttributeType.S),
                attributeDefinition("statusCreatedAtShortCodeGsi", ScalarAttributeType.S),
                attributeDefinition("activeRankingUserIdGsi", ScalarAttributeType.S),
                attributeDefinition("accessCount", ScalarAttributeType.N))
            .keySchema(keySchemaElement(URL_PRIMARY_KEY, KeyType.HASH))
            .globalSecondaryIndexes(
                GlobalSecondaryIndex.builder()
                    .indexName("user-index")
                    .keySchema(
                        keySchemaElement("userId", KeyType.HASH),
                        keySchemaElement("createdAtShortCodeGsi", KeyType.RANGE))
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                    .build(),
                GlobalSecondaryIndex.builder()
                    .indexName("user-status-index")
                    .keySchema(
                        keySchemaElement("userId", KeyType.HASH),
                        keySchemaElement("statusCreatedAtShortCodeGsi", KeyType.RANGE))
                    .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                    .build(),
                GlobalSecondaryIndex.builder()
                    .indexName("user-active-ranking-index")
                    .keySchema(
                        keySchemaElement("activeRankingUserIdGsi", KeyType.HASH),
                        keySchemaElement("accessCount", KeyType.RANGE))
                    .projection(
                        Projection.builder()
                            .projectionType(ProjectionType.INCLUDE)
                            .nonKeyAttributes(
                                "userId",
                                "originalUrl",
                                "createdAt",
                                "updatedAt",
                                "status",
                                "lastAccessedAt")
                            .build())
                    .build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());
  }

  private static void createCounterTableIfAbsent(DynamoDbClient dynamoDbClient) {
    if (tableExists(dynamoDbClient, COUNTER_TABLE_NAME)) {
      return;
    }

    dynamoDbClient.createTable(
        CreateTableRequest.builder()
            .tableName(COUNTER_TABLE_NAME)
            .attributeDefinitions(attributeDefinition(COUNTER_PRIMARY_KEY, ScalarAttributeType.S))
            .keySchema(keySchemaElement(COUNTER_PRIMARY_KEY, KeyType.HASH))
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .build());
  }

  private static boolean tableExists(DynamoDbClient dynamoDbClient, String tableName) {
    try {
      dynamoDbClient.describeTable(
          DescribeTableRequest.builder().tableName(tableName).build());
      return true;
    } catch (ResourceNotFoundException exception) {
      return false;
    }
  }

  private static void seedUrlCounterIfAbsent(DynamoDbClient dynamoDbClient) {
    try {
      dynamoDbClient.putItem(
          PutItemRequest.builder()
              .tableName(COUNTER_TABLE_NAME)
              .item(urlCounterItem())
              .conditionExpression("attribute_not_exists(#counterName)")
              .expressionAttributeNames(Map.of("#counterName", COUNTER_PRIMARY_KEY))
              .build());
    } catch (ConditionalCheckFailedException exception) {
      // The counter was already initialized.
    }
  }

  private static void seedUrlCounter(DynamoDbClient dynamoDbClient) {
    dynamoDbClient.putItem(
        PutItemRequest.builder().tableName(COUNTER_TABLE_NAME).item(urlCounterItem()).build());
  }

  private static Map<String, AttributeValue> urlCounterItem() {
    return Map.of(
        COUNTER_PRIMARY_KEY, stringAttribute(URL_COUNTER_NAME),
        "currentValue", numberAttribute(0),
        "description",
            stringAttribute("Global counter used to allocate URL short code ID blocks"));
  }

  private static void clearTable(
      DynamoDbClient dynamoDbClient, String tableName, String primaryKeyName) {
    Map<String, AttributeValue> lastEvaluatedKey = Map.of();

    do {
      var requestBuilder =
          ScanRequest.builder()
              .tableName(tableName)
              .consistentRead(true)
              .projectionExpression("#primaryKey")
              .expressionAttributeNames(Map.of("#primaryKey", primaryKeyName));

      if (!lastEvaluatedKey.isEmpty()) {
        requestBuilder.exclusiveStartKey(lastEvaluatedKey);
      }

      ScanResponse response = dynamoDbClient.scan(requestBuilder.build());
      for (Map<String, AttributeValue> item : response.items()) {
        dynamoDbClient.deleteItem(
            DeleteItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(primaryKeyName, item.get(primaryKeyName)))
                .build());
      }
      lastEvaluatedKey = response.lastEvaluatedKey();
    } while (lastEvaluatedKey != null && !lastEvaluatedKey.isEmpty());
  }

  private static AttributeDefinition attributeDefinition(
      String name, ScalarAttributeType attributeType) {
    return AttributeDefinition.builder().attributeName(name).attributeType(attributeType).build();
  }

  private static KeySchemaElement keySchemaElement(String name, KeyType keyType) {
    return KeySchemaElement.builder().attributeName(name).keyType(keyType).build();
  }

  private static AttributeValue stringAttribute(String value) {
    return AttributeValue.builder().s(value).build();
  }

  private static AttributeValue numberAttribute(long value) {
    return AttributeValue.builder().n(Long.toString(value)).build();
  }

  private static String createQueueIfAbsent(
      SqsClient sqsClient, String queueName, Map<QueueAttributeName, String> attributes) {
    try {
      return getQueueUrl(sqsClient, queueName);
    } catch (QueueDoesNotExistException exception) {
      return sqsClient
          .createQueue(
              CreateQueueRequest.builder().queueName(queueName).attributes(attributes).build())
          .queueUrl();
    }
  }

  private static Map<QueueAttributeName, String> defaultQueueAttributes() {
    return Map.of(
        QueueAttributeName.VISIBILITY_TIMEOUT, VISIBILITY_TIMEOUT,
        QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, RECEIVE_MESSAGE_WAIT_TIME_SECONDS,
        QueueAttributeName.MESSAGE_RETENTION_PERIOD, MESSAGE_RETENTION_PERIOD);
  }

  private static Map<QueueAttributeName, String> dlqQueueAttributes() {
    return Map.of(
        QueueAttributeName.VISIBILITY_TIMEOUT, VISIBILITY_TIMEOUT,
        QueueAttributeName.RECEIVE_MESSAGE_WAIT_TIME_SECONDS, RECEIVE_MESSAGE_WAIT_TIME_SECONDS,
        QueueAttributeName.MESSAGE_RETENTION_PERIOD, DLQ_MESSAGE_RETENTION_PERIOD);
  }

  private static void purgeQueue(SqsClient sqsClient, String queueName) {
    String queueUrl = getQueueUrl(sqsClient, queueName);
    sqsClient.purgeQueue(PurgeQueueRequest.builder().queueUrl(queueUrl).build());
  }

  private static String getQueueUrl(SqsClient sqsClient, String queueName) {
    return sqsClient
        .getQueueUrl(GetQueueUrlRequest.builder().queueName(queueName).build())
        .queueUrl();
  }
}
