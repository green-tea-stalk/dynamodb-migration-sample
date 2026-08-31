package com.example.dynamodb.repository.v2;

import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderKey;
import com.example.dynamodb.domain.OrderStatus;
import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchGetItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteItemEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;
import software.amazon.awssdk.enhanced.dynamodb.model.ScanEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AWS SDK v2 (DynamoDbEnhancedClient) implementation of
 * {@link OrderRepository}.
 */
public class V2DynamoDbEnhancedOrderRepository implements OrderRepository {

    /** Default DynamoDB table name */
    public static final String DEFAULT_TABLE_NAME = "orders";
    /** Status and OrderDate Global Secondary Index (GSI) name */
    public static final String GSI_STATUS_ORDER_DATE = "status-orderDate-index";

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<OrderItemV2> table;
    private final DynamoDbEnhancedClient batchEnhancedClient;
    private final DynamoDbTable<OrderItemV2> batchTable;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initializes the repository using the default table name ("orders").
     *
     * @param enhancedClient
     *            AWS SDK v2 {@link DynamoDbEnhancedClient} instance
     * @param batchEnhancedClient
     *            AWS SDK v2 {@link DynamoDbEnhancedClient} instance configured
     *            without extensions for batch operations
     */
    public V2DynamoDbEnhancedOrderRepository(DynamoDbEnhancedClient enhancedClient,
            DynamoDbEnhancedClient batchEnhancedClient) {
        this(enhancedClient, batchEnhancedClient, DEFAULT_TABLE_NAME);
    }

    /**
     * Initializes the repository with an explicit table name.
     *
     * @param enhancedClient
     *            AWS SDK v2 {@link DynamoDbEnhancedClient} instance
     * @param batchEnhancedClient
     *            AWS SDK v2 {@link DynamoDbEnhancedClient} instance configured
     *            without extensions for batch operations
     * @param tableName
     *            Target DynamoDB table name
     */
    public V2DynamoDbEnhancedOrderRepository(DynamoDbEnhancedClient enhancedClient,
            DynamoDbEnhancedClient batchEnhancedClient, String tableName) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(tableName, TableSchema.fromBean(OrderItemV2.class));
        this.batchEnhancedClient = batchEnhancedClient;
        this.batchTable = batchEnhancedClient.table(tableName, TableSchema.fromBean(OrderItemV2.class));
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException
     *             if order is null
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
        return findById(item.getCustomerId(), item.getOrderId()).orElseGet(item::toDomain);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Order> findById(String customerId, String orderId) {
        if (customerId == null || orderId == null) {
            return Optional.empty();
        }
        Key key = Key.builder().partitionValue(customerId).sortValue(orderId).build();

        OrderItemV2 item = table.getItem(r -> r.key(key).consistentRead(true));
        return Optional.ofNullable(item).map(OrderItemV2::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException
     *             if target order is not found
     * @throws software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException
     *             if version does not match expectedVersion
     */
    @Override
    public Order updateStatus(String customerId, String orderId, OrderStatus newStatus, long expectedVersion) {
        Key key = Key.builder().partitionValue(customerId).sortValue(orderId).build();

        OrderItemV2 item = table.getItem(r -> r.key(key).consistentRead(true));
        if (item == null) {
            throw new IllegalArgumentException("Order not found: customerId=" + customerId + ", orderId=" + orderId);
        }

        item.setStatus(newStatus);
        item.setVersion(expectedVersion);
        item.setUpdatedAt(Instant.now());

        // @DynamoDbVersionAttribute validates matching version and automatically
        // increments it on putItem.
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
        Key key = Key.builder().partitionValue(customerId).sortValue(orderId).build();

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
                .mappedTableResource(batchTable);

        for (Order order : orders) {
            OrderItemV2 item = OrderItemV2.fromDomain(order);
            if (item.getUpdatedAt() == null) {
                item.setUpdatedAt(Instant.now());
            }
            writeBatchBuilder.addPutItem(item);
        }

        BatchWriteItemEnhancedRequest batchRequest = BatchWriteItemEnhancedRequest.builder()
                .writeBatches(writeBatchBuilder.build()).build();

        batchEnhancedClient.batchWriteItem(batchRequest);
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
            Key ddbKey = Key.builder().partitionValue(key.customerId()).sortValue(key.orderId()).build();
            readBatchBuilder.addGetItem(ddbKey);
        }

        BatchGetItemEnhancedRequest batchGetRequest = BatchGetItemEnhancedRequest.builder()
                .readBatches(readBatchBuilder.build()).build();

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
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(customerId).build());

        QueryEnhancedRequest request = QueryEnhancedRequest.builder().queryConditional(queryConditional)
                .consistentRead(true).build();

        List<Order> results = new ArrayList<>();
        table.query(request).items().forEach(item -> results.add(item.toDomain()));
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> findByCustomerIdAndDateRange(String customerId, Instant from, Instant to) {
        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(customerId).build());

        Expression filterExpression = Expression.builder().expression("orderDate BETWEEN :from AND :to")
                .putExpressionValue(":from", AttributeValue.fromS(from.toString()))
                .putExpressionValue(":to", AttributeValue.fromS(to.toString())).build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder().queryConditional(queryConditional)
                .filterExpression(filterExpression).consistentRead(true).build();

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

        QueryConditional queryConditional = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(status.name()).build());

        QueryEnhancedRequest request = QueryEnhancedRequest.builder().queryConditional(queryConditional).build();

        List<Order> results = new ArrayList<>();
        index.query(request).forEach(page -> page.items().forEach(item -> results.add(item.toDomain())));
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> scanOrdersWithMinAmount(BigDecimal minAmount) {
        Expression filterExpression = Expression.builder().expression("totalAmount >= :minAmount")
                .putExpressionValue(":minAmount", AttributeValue.fromN(minAmount.toPlainString())).build();

        ScanEnhancedRequest request = ScanEnhancedRequest.builder().filterExpression(filterExpression).build();

        List<Order> results = new ArrayList<>();
        table.scan(request).items().forEach(item -> results.add(item.toDomain()));
        return results;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<Order> scanOrdersPaged(int pageSize, String paginationToken) {
        ScanEnhancedRequest.Builder requestBuilder = ScanEnhancedRequest.builder().limit(pageSize);

        if (paginationToken != null && !paginationToken.trim().isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodePaginationToken(paginationToken);
            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        Iterator<Page<OrderItemV2>> iterator = table.scan(requestBuilder.build()).iterator();

        if (!iterator.hasNext()) {
            return new PageResult<>(Collections.emptyList(), null);
        }

        Page<OrderItemV2> page = iterator.next();
        List<Order> orders = page.items().stream().map(OrderItemV2::toDomain).collect(Collectors.toList());

        String nextToken = encodePaginationToken(page.lastEvaluatedKey());
        return new PageResult<>(orders, nextToken);
    }

    /**
     * Encodes DynamoDB lastEvaluatedKey into a Base64 URL-safe pagination token
     * string.
     *
     * @param lastEvaluatedKey
     *            DynamoDB last evaluated key map
     * @return Base64 encoded pagination token (null if map is empty or null)
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
     * Decodes a Base64 URL-safe pagination token string into a DynamoDB
     * exclusiveStartKey map.
     *
     * @param token
     *            Base64 encoded pagination token string
     * @return Decoded DynamoDB attribute value map
     * @throws IllegalArgumentException
     *             if token decoding fails
     */
    private Map<String, AttributeValue> decodePaginationToken(String token) {
        try {
            byte[] jsonBytes = Base64.getUrlDecoder().decode(token);
            Map<String, String> simpleMap = objectMapper.readValue(jsonBytes, new TypeReference<Map<String, String>>() {
            });
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
