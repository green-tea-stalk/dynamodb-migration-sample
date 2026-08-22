package com.example.dynamodb.repository.v2;

import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderKey;
import com.example.dynamodb.domain.OrderStatus;
import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AWS SDK v2 (DynamoDbEnhancedClient) による OrderRepository 実装
 */
public class V2DynamoDbEnhancedOrderRepository implements OrderRepository {

    /** デフォルトのテーブル名 */
    public static final String DEFAULT_TABLE_NAME = "orders";
    /** ステータス・注文日時インデックス（GSI）の名前 */
    public static final String GSI_STATUS_ORDER_DATE = "status-orderDate-index";

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<OrderItemV2> table;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * デフォルトテーブル名（"orders"）を使用してリポジトリを初期化します。
     *
     * @param enhancedClient AWS SDK v2 の {@link DynamoDbEnhancedClient} インスタンス
     */
    public V2DynamoDbEnhancedOrderRepository(DynamoDbEnhancedClient enhancedClient) {
        this(enhancedClient, DEFAULT_TABLE_NAME);
    }

    /**
     * テーブル名を明示的に指定してリポジトリを初期化します。
     *
     * @param enhancedClient AWS SDK v2 の {@link DynamoDbEnhancedClient} インスタンス
     * @param tableName      対象の DynamoDB テーブル名
     */
    public V2DynamoDbEnhancedOrderRepository(DynamoDbEnhancedClient enhancedClient, String tableName) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(OrderItemV2.class));
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException order が null の場合
     */
    @Override
    public Order save(Order order) {
        if (order == null) {
            throw new IllegalArgumentException("Order cannot be null");
        }
        OrderItemV2 item = OrderItemV2.fromDomain(order);
        if (item.getUpdatedAt() == null) {
            item.setUpdatedAt(Instant.now());
        }
        table.putItem(item);
        return findById(item.getCustomerId(), item.getOrderId())
                .orElseGet(item::toDomain);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Order> findById(String customerId, String orderId) {
        if (customerId == null || orderId == null) {
            return Optional.empty();
        }
        Key key = Key.builder()
                .partitionValue(customerId)
                .sortValue(orderId)
                .build();

        OrderItemV2 item = table.getItem(r -> r.key(key).consistentRead(true));
        return Optional.ofNullable(item).map(OrderItemV2::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException 対象注文が存在しない場合
     * @throws software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException 期待されるバージョンと不一致の場合
     */
    @Override
    public Order updateStatus(String customerId, String orderId, OrderStatus newStatus, long expectedVersion) {
        Key key = Key.builder()
                .partitionValue(customerId)
                .sortValue(orderId)
                .build();

        OrderItemV2 item = table.getItem(r -> r.key(key).consistentRead(true));
        if (item == null) {
            throw new IllegalArgumentException("Order not found: customerId=" + customerId + ", orderId=" + orderId);
        }

        item.setStatus(newStatus);
        item.setVersion(expectedVersion);
        item.setUpdatedAt(Instant.now());

        // @DynamoDbVersionAttribute により、putItem 時に version の一致検証と自動インクリメントが実行されます。
        table.putItem(item);
        return findById(customerId, orderId).orElseGet(item::toDomain);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String customerId, String orderId) {
        if (customerId == null || orderId == null) {
            return;
        }
        Key key = Key.builder()
                .partitionValue(customerId)
                .sortValue(orderId)
                .build();

        table.deleteItem(key);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void batchSave(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        WriteBatch.Builder<OrderItemV2> writeBatchBuilder = WriteBatch.builder(OrderItemV2.class)
                .mappedTableResource(table);

        for (Order order : orders) {
            OrderItemV2 item = OrderItemV2.fromDomain(order);
            if (item.getUpdatedAt() == null) {
                item.setUpdatedAt(Instant.now());
            }
            writeBatchBuilder.addPutItem(item);
        }

        BatchWriteItemEnhancedRequest batchRequest = BatchWriteItemEnhancedRequest.builder()
                .writeBatches(writeBatchBuilder.build())
                .build();

        enhancedClient.batchWriteItem(batchRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> batchFindByIds(List<OrderKey> orderKeys) {
        if (orderKeys == null || orderKeys.isEmpty()) {
            return Collections.emptyList();
        }
        ReadBatch.Builder<OrderItemV2> readBatchBuilder = ReadBatch.builder(OrderItemV2.class)
                .mappedTableResource(table);

        for (OrderKey key : orderKeys) {
            Key ddbKey = Key.builder()
                    .partitionValue(key.customerId())
                    .sortValue(key.orderId())
                    .build();
            readBatchBuilder.addGetItem(ddbKey);
        }

        BatchGetItemEnhancedRequest batchGetRequest = BatchGetItemEnhancedRequest.builder()
                .readBatches(readBatchBuilder.build())
                .build();

        List<Order> results = new ArrayList<>();
        enhancedClient.batchGetItem(batchGetRequest).resultsForTable(table).forEach(item -> {
            if (item != null) {
                results.add(item.toDomain());
            }
        });
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeInTransaction(List<Order> ordersToWrite) {
        if (ordersToWrite == null || ordersToWrite.isEmpty()) {
            return;
        }
        TransactWriteItemsEnhancedRequest.Builder txBuilder = TransactWriteItemsEnhancedRequest.builder();

        for (Order order : ordersToWrite) {
            OrderItemV2 item = OrderItemV2.fromDomain(order);
            if (item.getUpdatedAt() == null) {
                item.setUpdatedAt(Instant.now());
            }
            txBuilder.addPutItem(table, item);
        }

        enhancedClient.transactWriteItems(txBuilder.build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> findByCustomerId(String customerId) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(customerId).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .consistentRead(true)
                .build();

        List<Order> results = new ArrayList<>();
        table.query(request).items().forEach(item -> results.add(item.toDomain()));
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> findByCustomerIdAndDateRange(String customerId, Instant from, Instant to) {
        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(customerId).build()
        );

        Expression filterExpression = Expression.builder()
                .expression("orderDate BETWEEN :from AND :to")
                .putExpressionValue(":from", AttributeValue.fromS(from.toString()))
                .putExpressionValue(":to", AttributeValue.fromS(to.toString()))
                .build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .consistentRead(true)
                .build();

        List<Order> results = new ArrayList<>();
        table.query(request).items().forEach(item -> results.add(item.toDomain()));
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> findByStatus(OrderStatus status) {
        DynamoDbIndex<OrderItemV2> index = table.index(GSI_STATUS_ORDER_DATE);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(status.name()).build()
        );

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .build();

        List<Order> results = new ArrayList<>();
        index.query(request).forEach(page ->
                page.items().forEach(item -> results.add(item.toDomain()))
        );
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> scanOrdersWithMinAmount(BigDecimal minAmount) {
        Expression filterExpression = Expression.builder()
                .expression("totalAmount >= :minAmount")
                .putExpressionValue(":minAmount", AttributeValue.fromN(minAmount.toPlainString()))
                .build();

        ScanEnhancedRequest request = ScanEnhancedRequest.builder()
                .filterExpression(filterExpression)
                .build();

        List<Order> results = new ArrayList<>();
        table.scan(request).items().forEach(item -> results.add(item.toDomain()));
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<Order> scanOrdersPaged(int pageSize, String paginationToken) {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder()
                .limit(pageSize);

        if (paginationToken != null && !paginationToken.trim().isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodePaginationToken(paginationToken);
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        Iterator<Page<OrderItemV2>> iterator = table.scan(requestBuilder.build()).iterator();

        if (!iterator.hasNext()) {
            return new PageResult<>(Collections.emptyList(), null);
        }

        Page<OrderItemV2> page = iterator.next();
        List<Order> orders = page.items().stream()
                .map(OrderItemV2::toDomain)
                .collect(Collectors.toList());

        String nextToken = encodePaginationToken(page.lastEvaluatedKey());
        return new PageResult<>(orders, nextToken);
    }

    /**
     * DynamoDB の lastEvaluatedKey を Base64 URL セーフ文字列のページネーショントークンにエンコードします。
     *
     * @param lastEvaluatedKey DynamoDB の最終評価キー
     * @return Base64 エンコードされたトークン文字列（キーが空または null の場合は null）
     */
    private String encodePaginationToken(Map<String, AttributeValue> lastEvaluatedKey) {
        if (lastEvaluatedKey == null || lastEvaluatedKey.isEmpty()) {
            return null;
        }
        try {
            Map<String, String> simpleMap = new HashMap<>();
            for (Map.Entry<String, AttributeValue> entry : lastEvaluatedKey.entrySet()) {
                if (entry.getValue().s() != null) {
                    simpleMap.put(entry.getKey(), "S:" + entry.getValue().s());
                } else if (entry.getValue().n() != null) {
                    simpleMap.put(entry.getKey(), "N:" + entry.getValue().n());
                }
            }
            byte[] jsonBytes = objectMapper.writeValueAsBytes(simpleMap);
            return Base64.getUrlEncoder().encodeToString(jsonBytes);
        } catch (Exception e) {
            throw new RuntimeException("Failed to encode pagination token", e);
        }
    }

    /**
     * Base64 URL セーフ文字列のページネーショントークンを DynamoDB の exclusiveStartKey にデコードします。
     *
     * @param token Base64 エンコードされたトークン文字列
     * @return 復元された DynamoDB の属性値マップ
     * @throws IllegalArgumentException トークンのデコードに失敗した場合
     */
    private Map<String, AttributeValue> decodePaginationToken(String token) {
        try {
            byte[] jsonBytes = Base64.getUrlDecoder().decode(token);
            Map<String, String> simpleMap = objectMapper.readValue(jsonBytes, new TypeReference<Map<String, String>>() {});
            Map<String, AttributeValue> map = new HashMap<>();
            for (Map.Entry<String, String> entry : simpleMap.entrySet()) {
                String val = entry.getValue();
                if (val.startsWith("S:")) {
                    map.put(entry.getKey(), AttributeValue.fromS(val.substring(2)));
                } else if (val.startsWith("N:")) {
                    map.put(entry.getKey(), AttributeValue.fromN(val.substring(2)));
                }
            }
            return map;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid pagination token: " + token, e);
        }
    }
}
