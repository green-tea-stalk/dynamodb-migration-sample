package com.example.dynamodb.repository;

import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderKey;
import com.example.dynamodb.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface providing data access operations for {@link Order}.
 * Defines domain boundaries completely decoupled from any specific AWS SDK
 * types.
 */
public interface OrderRepository {

    // =========================================================================
    // CRUD Operations
    // =========================================================================

    /**
     * Saves or updates an order (PutItem).
     *
     * @param order
     *            Order to save
     * @return Saved order instance
     */
    Order save(Order order);

    /**
     * Finds an order by customerId and orderId primary key (GetItem).
     *
     * @param customerId
     *            Customer ID (Partition Key)
     * @param orderId
     *            Order ID (Sort Key)
     * @return Optional containing the found order, or empty if not found
     */
    Optional<Order> findById(String customerId, String orderId);

    /**
     * Updates the status of an order with optimistic locking validation.
     *
     * @param customerId
     *            Customer ID
     * @param orderId
     *            Order ID
     * @param newStatus
     *            New order status
     * @param expectedVersion
     *            Expected current version number
     * @return Updated order instance
     */
    Order updateStatus(String customerId, String orderId, OrderStatus newStatus, long expectedVersion);

    /**
     * Deletes an order by primary key (DeleteItem).
     *
     * @param customerId
     *            Customer ID
     * @param orderId
     *            Order ID
     */
    void delete(String customerId, String orderId);

    // =========================================================================
    // Batch Operations
    // =========================================================================

    /**
     * Saves multiple orders in batches (BatchWriteItem).
     *
     * @param orders
     *            List of orders to save
     */
    void batchSave(List<Order> orders);

    /**
     * Finds multiple orders by composite keys in batches (BatchGetItem).
     *
     * @param orderKeys
     *            List of composite keys (customerId, orderId) to retrieve
     * @return List of retrieved orders (omits non-existent keys)
     */
    List<Order> batchFindByIds(List<OrderKey> orderKeys);

    // =========================================================================
    // Transaction Operations
    // =========================================================================

    /**
     * Atomically writes multiple orders in a single transaction
     * (TransactWriteItems). Rolls back all mutations if any condition check or
     * transaction participant fails.
     *
     * @param ordersToWrite
     *            List of orders to write in transaction
     */
    void executeInTransaction(List<Order> ordersToWrite);

    // =========================================================================
    // Query Operations
    // =========================================================================

    /**
     * Queries all orders for a given customer ID (Partition Key Query).
     *
     * @param customerId
     *            Customer ID
     * @return List of orders for the customer
     */
    List<Order> findByCustomerId(String customerId);

    /**
     * Queries orders for a customer placed within a specific date range.
     *
     * @param customerId
     *            Customer ID
     * @param from
     *            Start timestamp (inclusive)
     * @param to
     *            End timestamp (inclusive)
     * @return List of matching orders
     */
    List<Order> findByCustomerIdAndDateRange(String customerId, Instant from, Instant to);

    /**
     * Queries orders matching a specific status (GSI Query).
     *
     * @param status
     *            Order status
     * @return List of orders matching the status
     */
    List<Order> findByStatus(OrderStatus status);

    // =========================================================================
    // Scan Operations
    // =========================================================================

    /**
     * Scans orders with a filter on minimum total amount.
     *
     * @param minAmount
     *            Minimum order amount
     * @return List of matching orders
     */
    List<Order> scanOrdersWithMinAmount(BigDecimal minAmount);

    /**
     * Scans orders with pagination support.
     *
     * @param pageSize
     *            Maximum number of items per page
     * @param paginationToken
     *            Continuation token for fetching subsequent pages (null for first
     *            page)
     * @return Paginated result containing items and optional next token
     */
    PageResult<Order> scanOrdersPaged(int pageSize, String paginationToken);
}
