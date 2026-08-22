package com.example.dynamodb.repository.v1;

import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapper;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBMapperConfig;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBQueryExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBScanExpression;
import com.amazonaws.services.dynamodbv2.datamodeling.ScanResultPage;
import com.amazonaws.services.dynamodbv2.model.AttributeValue;
import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderKey;
import com.example.dynamodb.domain.OrderStatus;
import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.PageResult;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * AWS SDK v1 (DynamoDBMapper) implementation of {@link OrderRepository}.
 */
public class V1DynamoDbOrderRepository implements OrderRepository {

    private final DynamoDBMapper mapper;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initializes the repository using an {@link AmazonDynamoDB} client.
     *
     * @param dynamoDBClient
     *            AWS SDK v1 DynamoDB client
     */
    public V1DynamoDbOrderRepository(AmazonDynamoDB dynamoDBClient) {
        this(new DynamoDBMapper(dynamoDBClient, DynamoDBMapperConfig.DEFAULT));
    }

    /**
     * Initializes the repository using a configured {@link DynamoDBMapper}.
     *
     * @param mapper
     *            Configured {@link DynamoDBMapper}
     */
    public V1DynamoDbOrderRepository(DynamoDBMapper mapper) {
        this.mapper = mapper;
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
     * @throws IllegalArgumentException
     *             if the order is not found
     * @throws com.amazonaws.services.dynamodbv2.model.ConditionalCheckFailedException
     *             if the version number does not match expectedVersion
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

        // DynamoDBMapper validates matching version when @DynamoDBVersionAttribute is
        // present and increments it on save.
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
        mapper.delete(item,
                DynamoDBMapperConfig.builder().withSaveBehavior(DynamoDBMapperConfig.SaveBehavior.CLOBBER).build());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void batchSave(List<Order> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        List<OrderItemV1> items = orders.stream().map(order -> {
            OrderItemV1 item = OrderItemV1.fromDomain(order);
            if (item.getUpdatedAt() == null) {
                item.setUpdatedAt(Instant.now());
            }
            return item;
        }).collect(Collectors.toList());
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
        List<OrderItemV1> itemsToLoad = orderKeys.stream().map(key -> {
            OrderItemV1 item = new OrderItemV1();
            item.setCustomerId(key.customerId());
            item.setOrderId(key.orderId());
            return item;
        }).collect(Collectors.toList());

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
        com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest txRequest = new com.amazonaws.services.dynamodbv2.datamodeling.TransactionWriteRequest();

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
                .withHashKeyValues(hashKeyValues).withConsistentRead(true);

        return mapper.query(OrderItemV1.class, queryExpression).stream().map(OrderItemV1::toDomain)
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
                .withHashKeyValues(hashKeyValues).withFilterExpression("orderDate BETWEEN :from AND :to")
                .withExpressionAttributeValues(eav).withConsistentRead(true);

        return mapper.query(OrderItemV1.class, queryExpression).stream().map(OrderItemV1::toDomain)
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
                .withIndexName("status-orderDate-index").withHashKeyValues(hashKeyValues).withConsistentRead(false);

        return mapper.query(OrderItemV1.class, queryExpression).stream().map(OrderItemV1::toDomain)
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
                .withFilterExpression("totalAmount >= :minAmount").withExpressionAttributeValues(eav);

        return mapper.scan(OrderItemV1.class, scanExpression).stream().map(OrderItemV1::toDomain)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PageResult<Order> scanOrdersPaged(int pageSize, String paginationToken) {
        DynamoDBScanExpression scanExpression = new DynamoDBScanExpression().withLimit(pageSize);

        if (paginationToken != null && !paginationToken.trim().isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = decodePaginationToken(paginationToken);
            scanExpression.setExclusiveStartKey(exclusiveStartKey);
        }

        ScanResultPage<OrderItemV1> page = mapper.scanPage(OrderItemV1.class, scanExpression);

        List<Order> orders = page.getResults().stream().map(OrderItemV1::toDomain).collect(Collectors.toList());

        String nextToken = encodePaginationToken(page.getLastEvaluatedKey());
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
