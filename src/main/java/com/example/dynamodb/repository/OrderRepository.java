package com.example.dynamodb.repository;

import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderKey;
import com.example.dynamodb.domain.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * 注文データへのアクセスを提供するリポジトリインターフェース
 * AWS SDK の型に依存しないドメイン境界を定義します。
 */
public interface OrderRepository {

    // =========================================================================
    // CRUD 操作
    // =========================================================================

    /**
     * 注文を新規保存または更新します（PutItem）。
     *
     * @param order 保存対象の注文
     * @return 保存後の注文（楽観的ロックのバージョン番号が更新される）
     */
    Order save(Order order);

    /**
     * 顧客IDと注文ID（主キー）で注文を取得します（GetItem）。
     *
     * @param customerId 顧客ID (Partition Key)
     * @param orderId    注文ID (Sort Key)
     * @return 見つかった注文（存在しない場合は empty）
     */
    Optional<Order> findById(String customerId, String orderId);

    /**
     * 注文ステータスを楽観的ロック付きで更新します。
     *
     * @param customerId      顧客ID
     * @param orderId         注文ID
     * @param newStatus       新しいステータス
     * @param expectedVersion 期待される現在のバージョン番号
     * @return 更新後の注文
     */
    Order updateStatus(String customerId, String orderId, OrderStatus newStatus, long expectedVersion);

    /**
     * 注文を削除します（DeleteItem）。
     *
     * @param customerId 顧客ID
     * @param orderId    注文ID
     */
    void delete(String customerId, String orderId);

    // =========================================================================
    // Batch 操作
    // =========================================================================

    /**
     * 複数の注文を一括保存します（BatchWriteItem）。
     *
     * @param orders 保存対象の注文リスト
     */
    void batchSave(List<Order> orders);

    /**
     * 複数の主キーを指定して注文を一括取得します（BatchGetItem）。
     *
     * @param orderKeys 取得対象の主キー（customerId, orderId）リスト
     * @return 取得できた注文のリスト（存在しないキーのアイテムは含まれません）
     */
    List<Order> batchFindByIds(List<OrderKey> orderKeys);

    // =========================================================================
    // Transaction 操作
    // =========================================================================

    /**
     * 複数の注文の保存・更新を同一トランザクションでアトミックに実行します（TransactWriteItems）。
     * いずれかの書き込み条件（楽観的ロック等）が失敗した場合は、すべての変更がロールバックされます。
     *
     * @param ordersToWrite トランザクション内で保存・更新する注文リスト
     */
    void executeInTransaction(List<Order> ordersToWrite);

    // =========================================================================
    // Query 操作
    // =========================================================================

    /**
     * 顧客IDを指定してすべての注文を取得します（Partition Key Query）。
     *
     * @param customerId 顧客ID
     * @return 該当顧客の注文リスト
     */
    List<Order> findByCustomerId(String customerId);

    /**
     * 顧客IDと注文日時の範囲を指定して注文を取得します。
     *
     * @param customerId 顧客ID
     * @param from       開始日時（inclusive）
     * @param to         終了日時（inclusive）
     * @return 該当する注文リスト
     */
    List<Order> findByCustomerIdAndDateRange(String customerId, Instant from, Instant to);

    /**
     * 注文ステータスを指定して注文を取得します（GSI Query）。
     *
     * @param status 注文ステータス
     * @return 該当ステータスの注文リスト
     */
    List<Order> findByStatus(OrderStatus status);

    // =========================================================================
    // Scan 操作
    // =========================================================================

    /**
     * 指定金額以上の注文を検索します（Filter付き Scan）。
     *
     * @param minAmount 最小注文金額
     * @return 該当する注文リスト
     */
    List<Order> scanOrdersWithMinAmount(BigDecimal minAmount);

    /**
     * ページネーション付きで注文全件をスキャンします。
     *
     * @param pageSize        1ページあたりの取得件数
     * @param paginationToken 次ページ取得用のトークン（初回は null）
     * @return ページネーション結果
     */
    PageResult<Order> scanOrdersPaged(int pageSize, String paginationToken);
}
