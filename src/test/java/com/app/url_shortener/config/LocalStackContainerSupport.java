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

  private static final DockerImageName LOCALSTACK_IMAGE = parse("localstack/localstack:3.0");
  private static final String ACCESS_KEY = "test";
  private static final String SECRET_KEY = "test";
  private static final String URL_REDIRECT_EVENTS_QUEUE = "url-redirect-events-queue";
  private static final String URL_REDIRECT_EVENTS_DLQ = "url-redirect-events-dlq";
  private static final String EMAIL_VERIFICATION_EVENTS_QUEUE = "email-verification-events-queue";
  private static final String EMAIL_VERIFICATION_EVENTS_DLQ = "email-verification-events-dlq";
  private static final String SES_FROM_EMAIL = "no-reply@url-shortener.local";
  private static final String MESSAGE_RETENTION_PERIOD = "345600";
  private static final String RECEIVE_MESSAGE_WAIT_TIME_SECONDS = "20";
  private static final String VISIBILITY_TIMEOUT = "30";
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

  private LocalStackContainerSupport() {
  }

  public static void registerDynamoDbProperties(DynamicPropertyRegistry registry) {
    URI endpoint = LOCALSTACK_CONTAINER.getEndpointOverride(LocalStackContainer.Service.DYNAMODB);

    registry.add("aws.dynamodb.endpoint", endpoint::toString);
    registry.add("aws.dynamodb.region", LOCALSTACK_CONTAINER::getRegion);
    registry.add("aws.dynamodb.access-key", () -> ACCESS_KEY);
    registry.add("aws.dynamodb.secret-key", () -> SECRET_KEY);
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

  public static void setupDynamoDbTable() {
    URI endpoint = LOCALSTACK_CONTAINER.getEndpointOverride(LocalStackContainer.Service.DYNAMODB);
    String region = LOCALSTACK_CONTAINER.getRegion();

    DynamoDbClient dynamoDbClient =
            DynamoDbClient.builder()
                    .endpointOverride(endpoint)
                    .region(Region.of(region))
                    .credentialsProvider(
                            StaticCredentialsProvider.create(
                                    AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
                    .build();

    try {
      dynamoDbClient.describeTable(DescribeTableRequest.builder().tableName("url").build());
    } catch (ResourceNotFoundException e) {
      CreateTableRequest createRequest =
          CreateTableRequest.builder()
              .tableName("url")
              .attributeDefinitions(
                  AttributeDefinition.builder()
                      .attributeName("shortCode")
                      .attributeType(ScalarAttributeType.S)
                      .build(),
                  AttributeDefinition.builder()
                      .attributeName("userId")
                      .attributeType(ScalarAttributeType.S)
                      .build(),
                  AttributeDefinition.builder()
                      .attributeName("createdAtShortCodeGsi")
                      .attributeType(ScalarAttributeType.S)
                      .build(),
                  AttributeDefinition.builder()
                      .attributeName("statusCreatedAtShortCodeGsi")
                      .attributeType(ScalarAttributeType.S)
                      .build())
              .keySchema(
                  KeySchemaElement.builder()
                      .attributeName("shortCode")
                      .keyType(KeyType.HASH)
                      .build())
              .globalSecondaryIndexes(
                  GlobalSecondaryIndex.builder()
                      .indexName("user-index")
                      .keySchema(
                          KeySchemaElement.builder()
                              .attributeName("userId")
                              .keyType(KeyType.HASH)
                              .build(),
                          KeySchemaElement.builder()
                              .attributeName("createdAtShortCodeGsi")
                              .keyType(KeyType.RANGE)
                              .build())
                      .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                      .build(),
                  GlobalSecondaryIndex.builder()
                      .indexName("user-status-index")
                      .keySchema(
                          KeySchemaElement.builder()
                              .attributeName("userId")
                              .keyType(KeyType.HASH)
                              .build(),
                          KeySchemaElement.builder()
                              .attributeName("statusCreatedAtShortCodeGsi")
                              .keyType(KeyType.RANGE)
                              .build())
                      .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                      .build())
              .billingMode(BillingMode.PAY_PER_REQUEST)
              .build();

      dynamoDbClient.createTable(createRequest);
    } finally {
      dynamoDbClient.close();
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
    String dlqUrl = createQueueIfAbsent(sqsClient, dlqName, defaultQueueAttributes());
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

  private static SesClient createSesClient() {
    return SesClient.builder()
        .endpointOverride(sesEndpoint())
        .region(Region.of(LOCALSTACK_CONTAINER.getRegion()))
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(ACCESS_KEY, SECRET_KEY)))
        .build();
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
