package com.app.url_shortener.config;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;

import static org.testcontainers.utility.DockerImageName.parse;

public final class LocalStackContainerSupport {

  private static final DockerImageName LOCALSTACK_IMAGE = parse("localstack/localstack:3.0");
  private static final String ACCESS_KEY = "test";
  private static final String SECRET_KEY = "test";

  private static final LocalStackContainer LOCALSTACK_CONTAINER =
          new LocalStackContainer(LOCALSTACK_IMAGE).withServices(LocalStackContainer.Service.DYNAMODB);

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
}
