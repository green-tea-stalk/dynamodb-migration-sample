package com.example.dynamodb;

import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderKey;
import com.example.dynamodb.domain.OrderLineItem;
import com.example.dynamodb.domain.OrderStatus;
import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.PageResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Shared Contract Test suite verifying behavioral equivalence of
 * {@link OrderRepository}. Both v1 and v2 implementations must pass 100% of
 * these test cases to guarantee parity.
 */
public abstract class OrderRepositoryContractTest extends AbstractDynamoDbContainerTest {

    /** Test repository instance initialized before each test method */
    protected OrderRepository repository;

    /**
     * Factory method provided by subclasses to instantiate the target
     * {@link OrderRepository}.
     *
     * @return Target {@link OrderRepository} instance under test
     */
    protected abstract OrderRepository createRepository();

    @BeforeEach
    void setUp() {
        this.repository = createRepository();
        deleteAllItems();
    }

    @Test
    @DisplayName("CRUD: Save order and find by primary key (customerId, orderId)")
    void testSaveAndFindById() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Order order = new Order("cust-1001", "ord-5001", now, OrderStatus.CREATED, new BigDecimal("1500.00"),
                List.of(new OrderLineItem("item-1", "Kotlin in Action", 1, new BigDecimal("1000.00")),
                        new OrderLineItem("item-2", "AWS Architecture Guide", 1, new BigDecimal("500.00"))),
                null, now);

        Order saved = repository.save(order);
        assertThat(saved.getVersion()).isEqualTo(1L);

        Optional<Order> found = repository.findById("cust-1001", "ord-5001");
        assertThat(found).isPresent();

        Order actual = found.get();
        assertThat(actual.getCustomerId()).isEqualTo("cust-1001");
        assertThat(actual.getOrderId()).isEqualTo("ord-5001");
        assertThat(actual.getOrderDate()).isEqualTo(now);
        assertThat(actual.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(actual.getTotalAmount()).isEqualByComparingTo("1500.00");
        assertThat(actual.getItems()).hasSize(2);
        assertThat(actual.getItems().get(0).getItemName()).isEqualTo("Kotlin in Action");
        assertThat(actual.getItems().get(1).getItemName()).isEqualTo("AWS Architecture Guide");
        assertThat(actual.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("CRUD: Return empty Optional when primary key is not found")
    void testFindByIdNotFound() {
        Optional<Order> found = repository.findById("non-existent-cust", "non-existent-ord");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("CRUD: Optimistic locking update and version conflict detection")
    void testOptimisticLockingSuccessAndConflict() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Order order = new Order("cust-1002", "ord-5002", now, OrderStatus.CREATED, new BigDecimal("3000.00"),
                List.of(new OrderLineItem("item-3", "Cloud Architecture", 1, new BigDecimal("3000.00"))), null, now);
        repository.save(order);

        // 1st update: version 1 -> 2 (Success)
        Order updated = repository.updateStatus("cust-1002", "ord-5002", OrderStatus.PAID, 1L);
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(updated.getVersion()).isEqualTo(2L);

        // 2nd update with stale version 1L should throw conflict exception
        assertThatThrownBy(() -> repository.updateStatus("cust-1002", "ord-5002", OrderStatus.SHIPPED, 1L))
                .isInstanceOf(Exception.class);

        // Update with valid version 2L: version 2 -> 3 (Success)
        Order finalUpdated = repository.updateStatus("cust-1002", "ord-5002", OrderStatus.SHIPPED, 2L);
        assertThat(finalUpdated.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(finalUpdated.getVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("CRUD: Delete order by primary key")
    void testDelete() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Order order = new Order("cust-1003", "ord-5003", now, OrderStatus.CREATED, new BigDecimal("800.00"),
                List.of(new OrderLineItem("item-4", "Notebook", 2, new BigDecimal("400.00"))), null, now);
        repository.save(order);
        assertThat(repository.findById("cust-1003", "ord-5003")).isPresent();

        repository.delete("cust-1003", "ord-5003");
        assertThat(repository.findById("cust-1003", "ord-5003")).isEmpty();
    }

    @Test
    @DisplayName("Query: Find all orders by customerId (Partition Key Query)")
    void testFindByCustomerId() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        repository.save(new Order("cust-2001", "ord-1", now.minusSeconds(100), OrderStatus.CREATED,
                new BigDecimal("100"), null, null, now));
        repository.save(new Order("cust-2001", "ord-2", now.minusSeconds(50), OrderStatus.PAID, new BigDecimal("200"),
                null, null, now));
        repository.save(
                new Order("cust-2002", "ord-3", now, OrderStatus.CREATED, new BigDecimal("300"), null, null, now));

        List<Order> orders = repository.findByCustomerId("cust-2001");
        assertThat(orders).hasSize(2).extracting(Order::getOrderId).containsExactlyInAnyOrder("ord-1", "ord-2");
    }

    @Test
    @DisplayName("Query: Find orders by customerId and date range")
    void testFindByCustomerIdAndDateRange() {
        Instant baseTime = Instant.parse("2026-08-01T00:00:00Z");

        repository.save(new Order("cust-3001", "ord-1", baseTime.plus(1, ChronoUnit.DAYS), OrderStatus.CREATED,
                new BigDecimal("100"), null, null, baseTime));
        repository.save(new Order("cust-3001", "ord-2", baseTime.plus(3, ChronoUnit.DAYS), OrderStatus.PAID,
                new BigDecimal("200"), null, null, baseTime));
        repository.save(new Order("cust-3001", "ord-3", baseTime.plus(5, ChronoUnit.DAYS), OrderStatus.SHIPPED,
                new BigDecimal("300"), null, null, baseTime));

        List<Order> orders = repository.findByCustomerIdAndDateRange("cust-3001", baseTime.plus(2, ChronoUnit.DAYS),
                baseTime.plus(4, ChronoUnit.DAYS));

        assertThat(orders).hasSize(1);
        assertThat(orders.get(0).getOrderId()).isEqualTo("ord-2");
    }

    @Test
    @DisplayName("Query (GSI): Query orders by status using status-orderDate-index")
    void testFindByStatusGsi() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        repository.save(new Order("cust-4001", "ord-1", now.minusSeconds(100), OrderStatus.PAID, new BigDecimal("100"),
                null, null, now));
        repository.save(new Order("cust-4002", "ord-2", now.minusSeconds(50), OrderStatus.CREATED,
                new BigDecimal("200"), null, null, now));
        repository.save(new Order("cust-4003", "ord-3", now, OrderStatus.PAID, new BigDecimal("300"), null, null, now));

        List<Order> paidOrders = repository.findByStatus(OrderStatus.PAID);
        assertThat(paidOrders).hasSize(2).extracting(Order::getOrderId).containsExactlyInAnyOrder("ord-1", "ord-3");
    }

    @Test
    @DisplayName("Scan: Scan orders with minimum total amount filter")
    void testScanWithMinAmount() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        repository.save(
                new Order("cust-5001", "ord-1", now, OrderStatus.CREATED, new BigDecimal("500"), null, null, now));
        repository.save(
                new Order("cust-5002", "ord-2", now, OrderStatus.CREATED, new BigDecimal("1500"), null, null, now));
        repository.save(
                new Order("cust-5003", "ord-3", now, OrderStatus.CREATED, new BigDecimal("2500"), null, null, now));

        List<Order> highAmountOrders = repository.scanOrdersWithMinAmount(new BigDecimal("1000"));
        assertThat(highAmountOrders).hasSize(2).extracting(Order::getOrderId).containsExactlyInAnyOrder("ord-2",
                "ord-3");
    }

    @Test
    @DisplayName("Scan: Scan all orders using pagination tokens")
    void testScanOrdersPaged() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        for (int i = 1; i <= 5; i++) {
            repository.save(new Order("cust-page", "ord-" + i, now, OrderStatus.CREATED, BigDecimal.valueOf(i * 100),
                    null, null, now));
        }

        List<Order> allPagedOrders = new ArrayList<>();
        String nextToken = null;

        do {
            PageResult<Order> page = repository.scanOrdersPaged(2, nextToken);
            allPagedOrders.addAll(page.getItems());
            nextToken = page.getNextToken().orElse(null);
        } while (nextToken != null);

        assertThat(allPagedOrders).hasSize(5);
    }

    // =========================================================================
    // Batch Operations Tests
    // =========================================================================

    @Test
    @DisplayName("Batch: Batch save multiple orders and batch find by composite keys")
    void testBatchSaveAndBatchFindByIds() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        List<Order> orders = List.of(
                new Order("cust-batch-1", "ord-b1", now, OrderStatus.CREATED, new BigDecimal("1000.00"),
                        List.of(new OrderLineItem("item-b1", "Product 1", 1, new BigDecimal("1000.00"))), null, now),
                new Order("cust-batch-1", "ord-b2", now, OrderStatus.PAID, new BigDecimal("2000.00"),
                        List.of(new OrderLineItem("item-b2", "Product 2", 2, new BigDecimal("1000.00"))), null, now),
                new Order("cust-batch-2", "ord-b3", now, OrderStatus.SHIPPED, new BigDecimal("3000.00"),
                        List.of(new OrderLineItem("item-b3", "Product 3", 3, new BigDecimal("1000.00"))), null, now));

        // Batch save
        repository.batchSave(orders);

        // Batch find (requesting 2 keys)
        List<OrderKey> keysToFind = List.of(new OrderKey("cust-batch-1", "ord-b1"),
                new OrderKey("cust-batch-2", "ord-b3"));

        List<Order> found = repository.batchFindByIds(keysToFind);
        assertThat(found).hasSize(2).extracting(Order::getOrderId).containsExactlyInAnyOrder("ord-b1", "ord-b3");
    }

    @Test
    @DisplayName("Batch: Retrieve existing items when non-existent keys are included")
    void testBatchFindByIds_PartialAndNotFound() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        repository.save(new Order("cust-batch-3", "ord-b4", now, OrderStatus.CREATED, new BigDecimal("500.00"), null,
                null, now));

        List<OrderKey> keysToFind = List.of(new OrderKey("cust-batch-3", "ord-b4"),
                new OrderKey("non-existent-cust", "non-existent-ord"));

        List<Order> found = repository.batchFindByIds(keysToFind);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getOrderId()).isEqualTo("ord-b4");
    }

    // =========================================================================
    // Transaction Operations Tests
    // =========================================================================

    @Test
    @DisplayName("Transaction: Atomically write multiple orders in a single transaction")
    void testExecuteInTransaction_Success() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        List<Order> txOrders = List.of(
                new Order("cust-tx-1", "ord-tx1", now, OrderStatus.CREATED, new BigDecimal("1200.00"),
                        List.of(new OrderLineItem("item-tx1", "Tx Item 1", 1, new BigDecimal("1200.00"))), null, now),
                new Order("cust-tx-2", "ord-tx2", now, OrderStatus.PAID, new BigDecimal("2400.00"),
                        List.of(new OrderLineItem("item-tx2", "Tx Item 2", 2, new BigDecimal("1200.00"))), null, now));

        repository.executeInTransaction(txOrders);

        assertThat(repository.findById("cust-tx-1", "ord-tx1")).isPresent();
        assertThat(repository.findById("cust-tx-2", "ord-tx2")).isPresent();
    }
}
