package com.example.dynamodb.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Order domain model (pure POJO, independent of AWS SDK).
 */
@Data
@AllArgsConstructor
@Builder
public class Order {

    /**
     * Default constructor initializing an empty line items list.
     */
    public Order() {
        this.items = new ArrayList<>();
    }

    /** Customer ID (Partition Key) */
    private String customerId;

    /** Order ID (Sort Key) */
    private String orderId;

    /** Date and time when the order was placed */
    private Instant orderDate;

    /** Order status */
    private OrderStatus status;

    /** Total order amount */
    private BigDecimal totalAmount;

    /** List of order line items */
    @Builder.Default
    private List<OrderLineItem> items = new ArrayList<>();

    /** Version number for optimistic locking */
    private Long version;

    /** Timestamp when the order was last updated */
    private Instant updatedAt;

    /**
     * Returns the list of order line items.
     *
     * @return List of order line items (empty list if null)
     */
    public List<OrderLineItem> getItems() {
        return items != null ? items : Collections.emptyList();
    }

    /**
     * Sets the list of order line items.
     *
     * @param items
     *            List of order line items
     */
    public void setItems(List<OrderLineItem> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }
}
