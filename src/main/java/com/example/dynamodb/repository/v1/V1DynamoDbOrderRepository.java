package com.example.dynamodb.repository.v1;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.ScanResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.amazonaws.services.dynamodbv2.model.ComparisonOperator;
import com.amazonaws.services.dynamodbv2.model.Condition;
import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderKey;
import com.example.dynamodb.domain.OrderStatus;
import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * AWS SDK v1 (DynamoDBMapper) による OrderRepository 実装
 */
public class V1DynamoDbOrderRepository implements OrderRepository {

    private final DynamoDBMapper mapper;
    private final AmazonDynamoDB dynamoDBClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * {@link AmazonDynamoDB} クライアントを使用してリポジトリを初期化します。
     *
     * @param dynamoDBClient AWS SDK v1 の DynamoDB クライアント
     */
    public V1DynamoDbOrderRepository(AmazonDynamoDB dynamoDBClient) {
        this.dynamoDBClient = dynamoDBClient;
        this.mapper = new DynamoDBMapper(dynamoDBClient, DynamoDBMapperConfig.DEFAULT);
    }

    /**
     * {@link DynamoDBMapper} および {@link AmazonDynamoDB} を指定してリポジトリを初期化します。
     *
     * @param mapper         設定済みの {@link DynamoDBMapper}
     * @param dynamoDBClient AWS SDK v1 の DynamoDB クライアント
     */
    public V1DynamoDbOrderRepository(DynamoDBMapper mapper, AmazonDynamoDB dynamoDBClient) {
        this.mapper = mapper;
        this.dynamoDBClient = dynamoDBClient;
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
        OrderItemV1 item = OrderItemV1.fromDomain(order);
        if (item.getUpdatedAt() == null) {
            item.setUpdatedAt(Instant.now());
        }
        mapper.save(item);
        return item.toDomain();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<Order> findById(String customerId, String orderId) {
        if (customerId == null || orderId == null) {
            return Optional.empty();
        }
        OrderItemV1 item = mapper.load(OrderItemV1.class, customerId, orderId);
        return Optional.ofNullable(item).map(OrderItemV1::toDomain);
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException 対象注文が存在しない場合
     * @throws com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException 期待されるバージョンと不一致の場合
     */
    @Override
    public Order updateStatus(String customerId, String orderId, OrderStatus newStatus, long expectedVersion) {
        OrderItemV1 item = mapper.load(OrderItemV1.class, customerId, orderId);
        if (item == null) {
            throw new IllegalArgumentException("Order not found: customerId=" + customerId + ", orderId=" + orderId);
        }
        item.setStatus(newStatus);
        item.setVersion(expectedVersion);
        item.setUpdatedAt(Instant.now());

        // DynamoDBMapper は @DynamoDBVersionAttribute が付与されている場合、
        // 既存のバージョン番号と一致することを自動的に検証してインクリメント保存します。
        mapper.save(item);
        return item.toDomain();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void delete(String customerId, String orderId) {
        if (customerId == null || orderId == null) {
            return;
        }
        OrderItemV1 item = new OrderItemV1();
        item.setCustomerId(customerId);
        item.setOrderId(orderId);
        mapper.delete(item, DynamoDBMapperConfig.builder()
                .withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER)
                .build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void batchSave(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<OrderItemV1> items = orders.stream()
                .map(order -> {
                    OrderItemV1 item = OrderItemV1.fromDomain(order);
                    if (item.getUpdatedAt() == null) {
                        item.setUpdatedAt(Instant.now());
                    }
                    return item;
                })
                .collect(Collectors.toList());
        mapper.batchSave(items);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> batchFindByIds(List<OrderKey> orderKeys) {
        if (orderKeys == null || orderKeys.isEmpty()) {
            return Collections.emptyList();
        }
        List<OrderItemV1> itemsToLoad = orderKeys.stream()
                .map(key -> {
                    OrderItemV1 item = new OrderItemV1();
                    item.setCustomerId(key.customerId());
                    item.setOrderId(key.orderId());
                    return item;
                })
                .collect(Collectors.toList());

        Map<String, List<Object>> results = mapper.batchLoad(itemsToLoad);
        List<Order> orders = new ArrayList<>();
        for (List<Object> list : results.values()) {
            for (Object obj : list) {
                if (obj instanceof OrderItemV1 item) {
                    orders.add(item.toDomain());
                }
            }
        }
        return orders;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void executeInTransaction(List<Order> ordersToWrite) {
        if (ordersToWrite == null || ordersToWrite.isEmpty()) {
            return;
        }
        com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest txRequest =
                new com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest();

        for (Order order : ordersToWrite) {
            OrderItemV1 item = OrderItemV1.fromDomain(order);
            if (item.getUpdatedAt() == null) {
                item.setUpdatedAt(Instant.now());
            }
            txRequest.addPut(item);
        }
        mapper.transactionWrite(txRequest);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> findByCustomerId(String customerId) {
        OrderItemV1 hashKeyValues = new OrderItemV1();
        hashKeyValues.setCustomerId(customerId);

        DynamoDBQueryExpression<OrderItemV1> queryExpression = new DynamoDBQueryExpression<OrderItemV1>()
                .withHashKeyValues(hashKeyValues)
                .withConsistentRead(true);

        return mapper.query(OrderItemV1.class, queryExpression)
                .stream()
                .map(OrderItemV1::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> findByCustomerIdAndDateRange(String customerId, Instant from, Instant to) {
        OrderItemV1 hashKeyValues = new OrderItemV1();
        hashKeyValues.setCustomerId(customerId);

        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":from", new AttributeValue().withS(from.toString()));
        eav.put(":to", new AttributeValue().withS(to.toString()));

        DynamoDBQueryExpression<OrderItemV1> queryExpression = new DynamoDBQueryExpression<OrderItemV1>()
                .withHashKeyValues(hashKeyValues)
                .withFilterExpression("orderDate BETWEEN :from AND :to")
                .withExpressionAttributeValues(eav)
                .withConsistentRead(true);

        return mapper.query(OrderItemV1.class, queryExpression)
                .stream()
                .map(OrderItemV1::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> findByStatus(OrderStatus status) {
        OrderItemV1 hashKeyValues = new OrderItemV1();
        hashKeyValues.setStatus(status);

        DynamoDBQueryExpression<OrderItemV1> queryExpression = new DynamoDBQueryExpression<OrderItemV1>()
                .withIndexName("status-orderDate-index")
                .withHashKeyValues(hashKeyValues)
                .withConsistentRead(false);

        return mapper.query(OrderItemV1.class, queryExpression)
                .stream()
                .map(OrderItemV1::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Order> scanOrdersWithMinAmount(BigDecimal minAmount) {
        Map<String, AttributeValue> eav = new HashMap<>();
        eav.put(":minAmount", new AttributeValue().withN(minAmount.toPlainString()));

        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withFilterExpression("totalAmount >= :minAmount")
                .withExpressionAttributeValues(eav);

        return mapper.scan(OrderItemV1.class, scanExpression)
                .stream()
                .map(OrderItemV1::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<Order> scanOrdersPaged(int pageSize, String paginationToken) {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression()
                .withLimit(pageSize);

        if (paginationToken != null && !paginationToken.trim().isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodePaginationToken(paginationToken);
            scanExpression.setExclusiveStartKey(exclusiveStartKey);
        }

        ScanResultPage<OrderItemV1> page = mapper.scanPage(OrderItemV1.class, scanExpression);

        List<Order> orders = page.getResults().stream()
                .map(OrderItemV1::toDomain)
                .collect(Collectors.toList());

        String nextToken = encodePaginationToken(page.getLastEvaluatedKey());
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
                if (entry.getValue().getS() != null) {
                    simpleMap.put(entry.getKey(), "S:" + entry.getValue().getS());
                } else if (entry.getValue().getN() != null) {
                    simpleMap.put(entry.getKey(), "N:" + entry.getValue().getN());
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
                    map.put(entry.getKey(), new AttributeValue().withS(val.substring(2)));
                } else if (val.startsWith("N:")) {
                    map.put(entry.getKey(), new AttributeValue().withN(val.substring(2)));
                }
            }
            return map;
        } catch (IOException e) {
            throw new IllegalArgumentException("Invalid pagination token: " + token, e);
        }
    }
}
