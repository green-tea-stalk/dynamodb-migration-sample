package com.example.dynamodb.repository.v1;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBAttribute;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexHashKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBIndexRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBRangeKey;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTable;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverted;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConvertedEnum;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBTypeConverter;
import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBVersionAttribute;
import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderStatus;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DynamoDB Entity DTO for AWS SDK v1 (DynamoDBMapper).
 */
@DynamoDBTable(tableName = "orders")
@Setter
public class OrderItemV1 {

    /** Customer ID (Partition Key) */
    private String customerId;

    /** Order ID (Sort Key) */
    private String orderId;

    /** Order timestamp */
    private Instant orderDate;

    /** Order status */
    private OrderStatus status;

    /** Total order amount */
    private BigDecimal totalAmount;

    /** List of order line items */
    private List<OrderLineItemV1> items;

    /** Version number for optimistic locking */
    private Long version;

    /** Last updated timestamp */
    private Instant updatedAt;

    /**
     * Default constructor.
     */
    public OrderItemV1() {
        this.items = new ArrayList<>();
    }

    /**
     * Converts a domain model {@link Order} to an SDK v1 DTO entity.
     *
     * @param domain
     *            Source domain order model
     * @return Converted {@link OrderItemV1} instance (null if input is null)
     */
    public static OrderItemV1 fromDomain(Order domain) {
        if (domain == null) {
            return null;
        }
        OrderItemV1 item = new OrderItemV1();
        item.setCustomerId(domain.getCustomerId());
        item.setOrderId(domain.getOrderId());
        item.setOrderDate(domain.getOrderDate());
        item.setStatus(domain.getStatus());
        item.setTotalAmount(domain.getTotalAmount());
        if (domain.getItems() != null) {
            item.setItems(domain.getItems().stream().map(OrderLineItemV1::fromDomain).collect(Collectors.toList()));
        }
        item.setVersion(domain.getVersion());
        item.setUpdatedAt(domain.getUpdatedAt());
        return item;
    }

    /**
     * Converts this SDK v1 DTO entity to a domain model {@link Order}.
     *
     * @return Converted domain order model
     */
    public Order toDomain() {
        return new Order(customerId, orderId, orderDate, status, totalAmount,
                items != null
                        ? items.stream().map(OrderLineItemV1::toDomain).collect(Collectors.toList())
                        : Collections.emptyList(),
                version, updatedAt);
    }

    /**
     * Returns the customer ID (Partition Key).
     *
     * @return Customer ID
     */
    @DynamoDBHashKey(attributeName = "customerId")
    public String getCustomerId() {
        return customerId;
    }

    /**
     * Returns the order ID (Sort Key).
     *
     * @return Order ID
     */
    @DynamoDBRangeKey(attributeName = "orderId")
    public String getOrderId() {
        return orderId;
    }

    /**
     * Returns the order date. Also functions as GSI Sort Key.
     *
     * @return Order date
     */
    @DynamoDBTypeConverted(converter = InstantTypeConverter.class)
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "status-orderDate-index", attributeName = "orderDate")
    @DynamoDBAttribute(attributeName = "orderDate")
    public Instant getOrderDate() {
        return orderDate;
    }

    /**
     * Returns the order status. Also functions as GSI Partition Key.
     *
     * @return Order status
     */
    @DynamoDBTypeConvertedEnum
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "status-orderDate-index", attributeName = "status")
    @DynamoDBAttribute(attributeName = "status")
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Returns the total order amount.
     *
     * @return Total amount
     */
    @DynamoDBAttribute(attributeName = "totalAmount")
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Returns the list of order line items.
     *
     * @return List of order line items
     */
    @DynamoDBAttribute(attributeName = "items")
    public List<OrderLineItemV1> getItems() {
        return items;
    }

    /**
     * Sets the list of order line items.
     *
     * @param items
     *            List of order line items
     */
    public void setItems(List<OrderLineItemV1> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    /**
     * Returns the version number for optimistic locking.
     *
     * @return Version number
     */
    @DynamoDBVersionAttribute(attributeName = "version")
    public Long getVersion() {
        return version;
    }

    /**
     * Returns the last updated timestamp.
     *
     * @return Last updated timestamp
     */
    @DynamoDBTypeConverted(converter = InstantTypeConverter.class)
    @DynamoDBAttribute(attributeName = "updatedAt")
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * Custom type converter between {@link Instant} and ISO-8601 string
     * representation.
     */
    public static class InstantTypeConverter implements DynamoDBTypeConverter<String, Instant> {

        /**
         * Default constructor.
         */
        public InstantTypeConverter() {
        }

        /**
         * Converts {@link Instant} to ISO-8601 string.
         *
         * @param object
         *            Source {@link Instant}
         * @return ISO-8601 formatted string (null if input is null)
         */
        @Override
        public String convert(Instant object) {
            return object != null ? object.toString() : null;
        }

        /**
         * Converts ISO-8601 string back to {@link Instant}.
         *
         * @param object
         *            Source ISO-8601 string
         * @return Parsed {@link Instant} (null if input is null)
         */
        @Override
        public Instant unconvert(String object) {
            return object != null ? Instant.parse(object) : null;
        }
    }
}
