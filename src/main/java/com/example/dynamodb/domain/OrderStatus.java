package com.example.dynamodb.domain;

/**
 * Enumeration representing order status.
 */
public enum OrderStatus {
    /** Order created */
    CREATED,
    /** Payment completed */
    PAID,
    /** Order shipped */
    SHIPPED,
    /** Order delivered */
    DELIVERED,
    /** Order cancelled */
    CANCELLED
}
