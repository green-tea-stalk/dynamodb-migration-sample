package com.example.dynamodb.domain;

/**
 * Immutable composite key record (Partition Key + Sort Key) representing an
 * order.
 *
 * @param customerId
 *            Customer ID (Partition Key)
 * @param orderId
 *            Order ID (Sort Key)
 */
public record OrderKey(String customerId, String orderId) {
}
