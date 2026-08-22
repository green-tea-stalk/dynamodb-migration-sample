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
 * OrderRepository の振る舞いを検証する共通契約テスト（Contract Test） v1 実装と v2 実装の両方がこのテストスイートを
 * 100% パスすることで等価性を保証します。
 */
public abstract class OrderRepositoryContractTest extends AbstractDynamoDbContainerTest {

    /** テスト対象のリポジトリインスタンス（各テストメソッド実行前に setUp で初期化） */
    protected OrderRepository repository;

    /**
     * テスト対象の {@link OrderRepository} 実装インスタンスをサブクラスで生成して提供します。
     *
     * @return テスト対象の {@link OrderRepository} インスタンス
     */
    protected abstract OrderRepository createRepository();

    @BeforeEach
    void setUp() {
        this.repository = createRepository();
        deleteAllItems();
    }

    @Test
    @DisplayName("CRUD: 注文を保存し、主キー（customerId, orderId）で取得できる")
    void testSaveAndFindById() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Order order = new Order("cust-1001", "ord-5001", now, OrderStatus.CREATED, new BigDecimal("1500.00"),
                List.of(new OrderLineItem("item-1", "Kotlin入門", 1, new BigDecimal("1000.00")),
                        new OrderLineItem("item-2", "AWS設計ガイド", 1, new BigDecimal("500.00"))),
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
        assertThat(actual.getItems().get(0).getItemName()).isEqualTo("Kotlin入門");
        assertThat(actual.getItems().get(1).getItemName()).isEqualTo("AWS設計ガイド");
        assertThat(actual.getVersion()).isEqualTo(1L);
    }

    @Test
    @DisplayName("CRUD: 存在しない主キーで検索した場合は empty を返す")
    void testFindByIdNotFound() {
        Optional<Order> found = repository.findById("non-existent-cust", "non-existent-ord");
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("CRUD: 楽観的ロック（@Version）による更新と競合検知")
    void testOptimisticLockingSuccessAndConflict() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Order order = new Order("cust-1002", "ord-5002", now, OrderStatus.CREATED, new BigDecimal("3000.00"),
                List.of(new OrderLineItem("item-3", "クラウドアーキテクチャ", 1, new BigDecimal("3000.00"))), null, now);
        repository.save(order);

        // 1回目の更新: version 1 -> 2 (正常)
        Order updated = repository.updateStatus("cust-1002", "ord-5002", OrderStatus.PAID, 1L);
        assertThat(updated.getStatus()).isEqualTo(OrderStatus.PAID);
        assertThat(updated.getVersion()).isEqualTo(2L);

        // 2回目の更新で古い version 1L を指定した場合に競合例外が発生すること
        assertThatThrownBy(() -> repository.updateStatus("cust-1002", "ord-5002", OrderStatus.SHIPPED, 1L))
                .isInstanceOf(Exception.class);

        // 正しい version 2L で更新: version 2 -> 3 (正常)
        Order finalUpdated = repository.updateStatus("cust-1002", "ord-5002", OrderStatus.SHIPPED, 2L);
        assertThat(finalUpdated.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(finalUpdated.getVersion()).isEqualTo(3L);
    }

    @Test
    @DisplayName("CRUD: 注文を削除できる")
    void testDelete() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);
        Order order = new Order("cust-1003", "ord-5003", now, OrderStatus.CREATED, new BigDecimal("800.00"),
                List.of(new OrderLineItem("item-4", "ノート", 2, new BigDecimal("400.00"))), null, now);
        repository.save(order);
        assertThat(repository.findById("cust-1003", "ord-5003")).isPresent();

        repository.delete("cust-1003", "ord-5003");
        assertThat(repository.findById("cust-1003", "ord-5003")).isEmpty();
    }

    @Test
    @DisplayName("Query: 顧客ID（Partition Key）ですべての注文を取得できる")
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
    @DisplayName("Query: 顧客IDと注文日時範囲（Filter / Date Range）で注文を取得できる")
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
    @DisplayName("Query (GSI): 注文ステータス（status-orderDate-index）で注文を検索できる")
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
    @DisplayName("Scan: 金額フィルタ（FilterExpression）付きスキャン")
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
    @DisplayName("Scan: ページネーション付きスキャンで全件を取得できる")
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
    // Batch 操作テスト
    // =========================================================================

    @Test
    @DisplayName("Batch: 複数の注文を一括保存（batchSave）し、一括取得（batchFindByIds）できる")
    void testBatchSaveAndBatchFindByIds() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        List<Order> orders = List.of(
                new Order("cust-batch-1", "ord-b1", now, OrderStatus.CREATED, new BigDecimal("1000.00"),
                        List.of(new OrderLineItem("item-b1", "商品1", 1, new BigDecimal("1000.00"))), null, now),
                new Order("cust-batch-1", "ord-b2", now, OrderStatus.PAID, new BigDecimal("2000.00"),
                        List.of(new OrderLineItem("item-b2", "商品2", 2, new BigDecimal("1000.00"))), null, now),
                new Order("cust-batch-2", "ord-b3", now, OrderStatus.SHIPPED, new BigDecimal("3000.00"),
                        List.of(new OrderLineItem("item-b3", "商品3", 3, new BigDecimal("1000.00"))), null, now));

        // 一括保存
        repository.batchSave(orders);

        // 一括取得（2件のみ指定）
        List<OrderKey> keysToFind = List.of(new OrderKey("cust-batch-1", "ord-b1"),
                new OrderKey("cust-batch-2", "ord-b3"));

        List<Order> found = repository.batchFindByIds(keysToFind);
        assertThat(found).hasSize(2).extracting(Order::getOrderId).containsExactlyInAnyOrder("ord-b1", "ord-b3");
    }

    @Test
    @DisplayName("Batch: 存在しない主キーが含まれていても存在する分のみ取得できる")
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
    // Transaction 操作テスト
    // =========================================================================

    @Test
    @DisplayName("Transaction: 複数の注文をアトミックに一括書き込みできる")
    void testExecuteInTransaction_Success() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MILLIS);

        List<Order> txOrders = List.of(
                new Order("cust-tx-1", "ord-tx1", now, OrderStatus.CREATED, new BigDecimal("1200.00"),
                        List.of(new OrderLineItem("item-tx1", "トランザクション商品1", 1, new BigDecimal("1200.00"))), null, now),
                new Order("cust-tx-2", "ord-tx2", now, OrderStatus.PAID, new BigDecimal("2400.00"),
                        List.of(new OrderLineItem("item-tx2", "トランザクション商品2", 2, new BigDecimal("1200.00"))), null,
                        now));

        repository.executeInTransaction(txOrders);

        assertThat(repository.findById("cust-tx-1", "ord-tx1")).isPresent();
        assertThat(repository.findById("cust-tx-2", "ord-tx2")).isPresent();
    }
}
