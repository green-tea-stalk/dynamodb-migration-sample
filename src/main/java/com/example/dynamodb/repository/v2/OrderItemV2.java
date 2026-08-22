package com.example.dynamodb.repository.v2;

import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderStatus;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * DynamoDB Entity DTO for AWS SDK v2 (DynamoDbEnhancedClient).
 */
@DynamoDbBean
@Setter
public class OrderItemV2 {

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
    private List<OrderLineItemV2> items;

    /** Version number for optimistic locking */
    private Long version;

    /** Last updated timestamp */
    private Instant updatedAt;

    /**
     * Default constructor.
     */
    public OrderItemV2() {
        this.items = new ArrayList<>();
    }

    /**
     * Converts a domain model {@link Order} to an SDK v2 DTO entity.
     *
     * @param domain
     *            Source domain order model
     * @return Converted {@link OrderItemV2} instance (null if input is null)
     */
    public static OrderItemV2 fromDomain(Order domain) {
        if (domain == null) {
            return null;
        }
        OrderItemV2 item = new OrderItemV2();
        item.setCustomerId(domain.getCustomerId());
        item.setOrderId(domain.getOrderId());
        item.setOrderDate(domain.getOrderDate());
        item.setStatus(domain.getStatus());
        item.setTotalAmount(domain.getTotalAmount());
        if (domain.getItems() != null) {
            item.setItems(domain.getItems().stream().map(OrderLineItemV2::fromDomain).collect(Collectors.toList()));
        }
        item.setVersion(domain.getVersion());
        item.setUpdatedAt(domain.getUpdatedAt());
        return item;
    }

    /**
     * Converts this SDK v2 DTO entity to a domain model {@link Order}.
     *
     * @return Converted domain order model
     */
    public Order toDomain() {
        return new Order(customerId, orderId, orderDate, status, totalAmount,
                items != null
                        ? items.stream().map(OrderLineItemV2::toDomain).collect(Collectors.toList())
                        : Collections.emptyList(),
                version, updatedAt);
    }

    /**
     * Returns the customer ID (Partition Key).
     *
     * @return Customer ID
     */
    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    public String getCustomerId() {
        return customerId;
    }

    /**
     * Returns the order ID (Sort Key).
     *
     * @return Order ID
     */
    @DynamoDbSortKey
    @DynamoDbAttribute("orderId")
    public String getOrderId() {
        return orderId;
    }

    /**
     * Returns the order date. Also functions as GSI Sort Key.
     *
     * @return Order date
     */
    @DynamoDbSecondarySortKey(indexNames = "status-orderDate-index")
    @DynamoDbAttribute("orderDate")
    public Instant getOrderDate() {
        return orderDate;
    }

    /**
     * Returns the order status. Also functions as GSI Partition Key.
     *
     * @return Order status
     */
    @DynamoDbSecondaryPartitionKey(indexNames = "status-orderDate-index")
    @DynamoDbAttribute("status")
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Returns the total order amount.
     *
     * @return Total amount
     */
    @DynamoDbAttribute("totalAmount")
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * Returns the list of order line items.
     *
     * @return List of order line items
     */
    @DynamoDbAttribute("items")
    public List<OrderLineItemV2> getItems() {
        return items;
    }

    /**
     * Sets the list of order line items.
     *
     * @param items
     *            List of order line items
     */
    public void setItems(List<OrderLineItemV2> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    /**
     * Returns the version number for optimistic locking.
     *
     * @return Version number
     */
    @DynamoDbVersionAttribute
    @DynamoDbAttribute("version")
    public Long getVersion() {
        return version;
    }

    /**
     * Returns the last updated timestamp.
     *
     * @return Last updated timestamp
     */
    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
