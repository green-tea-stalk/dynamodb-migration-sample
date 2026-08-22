package com.example.dynamodb;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.example.dynamodb.config.DynamoDbV1Config;
import com.example.dynamodb.config.DynamoDbV2Config;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;

import java.net.URI;
import java.util.List;

/**
 * Testcontainers を用いて DynamoDB Local を起動し、テーブル初期化を行う基底テストクラス
 */
@Testcontainers
public abstract class AbstractDynamoDbContainerTest {

    /** テスト対象の DynamoDB テーブル名 */
    public static final String TABLE_NAME = "orders";
    /** ステータス・注文日時 GSI のインデックス名 */
    public static final String GSI_STATUS_ORDER_DATE = "status-orderDate-index";

    /** DynamoDB Local を実行する Testcontainers コンテナインスタンス */
    @Container
    public static final GenericContainer<?> DYNAMODB_CONTAINER =
            new GenericContainer<>("amazon/dynamodb-local:latest")
                    .withExposedPorts(8000)
                    .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forListeningPort());

    /** サブクラスで利用可能な AWS SDK v1 AmazonDynamoDB クライアント */
    protected static AmazonDynamoDB v1Client;
    /** サブクラスで利用可能な AWS SDK v2 DynamoDbClient クライアント */
    protected static DynamoDbClient v2Client;
    /** サブクラスで利用可能な AWS SDK v2 DynamoDbEnhancedClient クライアント */
    protected static DynamoDbEnhancedClient v2EnhancedClient;

    @BeforeAll
    static void startContainerAndSetupTable() {
        if (!DYNAMODB_CONTAINER.isRunning()) {
            DYNAMODB_CONTAINER.start();
        }

        String endpointUrl = "http://" + DYNAMODB_CONTAINER.getHost() + ":" + DYNAMODB_CONTAINER.getMappedPort(8000);
        URI endpoint = URI.create(endpointUrl);

        // v1 / v2 クライアント初期化
        v1Client = DynamoDbV1Config.createClient(endpoint, "us-east-1", "dummyKey", "dummySecret");
        v2Client = DynamoDbV2Config.createClient(endpoint, "us-east-1", "dummyKey", "dummySecret");
        v2EnhancedClient = DynamoDbV2Config.createEnhancedClient(v2Client);

        // テーブル作成
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
     * テスト用 orders テーブルおよび GSI を作成します。
     */
    private static void createOrdersTable() {
        try {
            // テーブル作成リクエスト (v2 SDK を使用して作成)
            CreateTableRequest createTableRequest = CreateTableRequest.builder()
                    .tableName(TABLE_NAME)
                    .keySchema(
                            KeySchemaElement.builder().attributeName("customerId").keyType(KeyType.HASH).build(),
                            KeySchemaElement.builder().attributeName("orderId").keyType(KeyType.RANGE).build()
                    )
                    .attributeDefinitions(
                            AttributeDefinition.builder().attributeName("customerId").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("orderId").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("status").attributeType(ScalarAttributeType.S).build(),
                            AttributeDefinition.builder().attributeName("orderDate").attributeType(ScalarAttributeType.S).build()
                    )
                    .globalSecondaryIndexes(
                            GlobalSecondaryIndex.builder()
                                     .indexName(GSI_STATUS_ORDER_DATE)
                                     .keySchema(
                                             KeySchemaElement.builder().attributeName("status").keyType(KeyType.HASH).build(),
                                             KeySchemaElement.builder().attributeName("orderDate").keyType(KeyType.RANGE).build()
                                     )
                                     .projection(Projection.builder().projectionType(ProjectionType.ALL).build())
                                     .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                                     .build()
                    )
                    .provisionedThroughput(ProvisionedThroughput.builder().readCapacityUnits(5L).writeCapacityUnits(5L).build())
                    .build();

            v2Client.createTable(createTableRequest);
        } catch (ResourceInUseException e) {
            // テーブルが既に存在する場合はスキップ
        }
    }

    /**
     * テストケース間でテーブル内の全アイテムを削除し、データをクリーンアップします。
     */
    protected void deleteAllItems() {
        // テストケース間のデータクリーンアップ
        ScanResponse scanResponse = v2Client.scan(ScanRequest.builder().tableName(TABLE_NAME).build());
        for (var item : scanResponse.items()) {
            v2Client.deleteItem(DeleteItemRequest.builder()
                    .tableName(TABLE_NAME)
                    .key(java.util.Map.of(
                            "customerId", item.get("customerId"),
                            "orderId", item.get("orderId")
                    ))
                    .build());
        }
    }
}
