package com.example.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.example.dynamodb.config.DynamoDbV1Config;
import com.example.dynamodb.config.DynamoDbV2Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeDefinition;
import software.amazon.awssdk.services.dynamodb.model.CreateTableRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GlobalSecondaryIndex;
import software.amazon.awssdk.services.dynamodb.model.KeySchemaElement;
import software.amazon.awssdk.services.dynamodb.model.KeyType;
import software.amazon.awssdk.services.dynamodb.model.Projection;
import software.amazon.awssdk.services.dynamodb.model.ProjectionType;
import software.amazon.awssdk.services.dynamodb.model.ProvisionedThroughput;
import software.amazon.awssdk.services.dynamodb.model.ResourceInUseException;
import software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;

import java.net.URI;
import java.util.Map;

/**
 * Base test class that starts DynamoDB Local via Testcontainers and creates
 * required DynamoDB tables.
 */
@Testcontainers
public abstract class AbstractDynamoDbContainerTest {

    /** Target DynamoDB table name */
    public static final String TABLE_NAME = "orders";
    /** Status and OrderDate Global Secondary Index (GSI) name */
    public static final String GSI_STATUS_ORDER_DATE = "status-orderDate-index";

    /** Testcontainers container instance running DynamoDB Local */
    @Container
    @SuppressWarnings("resource")
    public static final GenericContainer<?> DYNAMODB_CONTAINER = new GenericContainer<>(
            DockerImageName.parse("amazon/dynamodb-local:latest")).withExposedPorts(8000)
            .waitingFor(Wait.forListeningPort());

    /** AWS SDK v1 AmazonDynamoDB client available to subclasses */
    protected static AmazonDynamoDB v1Client;
    /** AWS SDK v2 DynamoDbClient client available to subclasses */
    protected static DynamoDbClient v2Client;
    /** AWS SDK v2 DynamoDbEnhancedClient client available to subclasses */
    protected static DynamoDbEnhancedClient v2EnhancedClient;
    /**
     * AWS SDK v2 DynamoDbEnhancedClient client configured without extensions for
     * batch operations
     */
    protected static DynamoDbEnhancedClient v2BatchEnhancedClient;

    @BeforeAll
    static void startContainerAndSetupTable() {
        if (!DYNAMODB_CONTAINER.isRunning()) {
            DYNAMODB_CONTAINER.start();
        }

        String endpointUrl = "http://" + DYNAMODB_CONTAINER.getHost() + ":" + DYNAMODB_CONTAINER.getMappedPort(8000);
        URI endpoint = URI.create(endpointUrl);

        // Initialize v1 and v2 clients
        v1Client = DynamoDbV1Config.createClient(endpoint, "us-east-1", "dummyKey", "dummySecret");
        v2Client = DynamoDbV2Config.createClient(endpoint, "us-east-1", "dummyKey", "dummySecret");
        v2EnhancedClient = DynamoDbV2Config.createEnhancedClient(v2Client);
        v2BatchEnhancedClient = DynamoDbV2Config.createBatchEnhancedClient(v2Client);

        // Create table
        createOrdersTable();
    }

    @AfterAll
    static void cleanUpClients() {
        if (v2Client != null) {
            v2Client.close();
        }
        if (v1Client != null) {
            v1Client.shutdown();
        }
    }

    /**
     * Creates test orders table and Global Secondary Index using SDK v2.
     */
    private static void createOrdersTable() {
        try {
            CreateTableRequest createTableRequest = CreateTableRequest.builder().tableName(TABLE_NAME)
                    .keySchema(KeySchemaElement.builder().attributeName("customerId").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("orderId").keyType(KeyType.RANGE).build())
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("customerId")
                                    .attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("orderId").attributeType(ScalarAttributeType.S)
                                    .build(),
                            AttributeDefinition.builder().attributeName("status").attributeType(ScalarAttributeType.S)
                                    .build(),
                            AttributeDefinition.builder().attributeName("orderDate")
                                    .attributeType(ScalarAttributeType.S).build())
                    .globalSecondaryIndexes(GlobalSecondaryIndex.builder().indexName(GSI_STATUS_ORDER_DATE)
                            .keySchema(KeySchemaElement.builder().attributeName("status").keyType(KeyType.HASH).build(),
                                    KeySchemaElement.builder().attributeName("orderDate").keyType(KeyType.RANGE)
                                            .build())
                            .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                            .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L)
                                    .writeCapacityUnits(5L).build())
                            .build())
                    .provisionedThroughput(
                            ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                    .build();

            v2Client.createTable(createTableRequest);
        } catch (ResourceInUseException e) {
            // Table already exists, skip
        }
    }

    /**
     * Deletes all items in the orders table to clean up state between test runs.
     */
    protected void deleteAllItems() {
        ScanResponse scanResponse = v2Client.scan(ScanRequest.builder().tableName(TABLE_NAME).build());
        for (var item : scanResponse.items()) {
            v2Client.deleteItem(DeleteItemRequest.builder().tableName(TABLE_NAME)
                    .key(Map.of("customerId", item.get("customerId"), "orderId", item.get("orderId"))).build());
        }
    }
}
