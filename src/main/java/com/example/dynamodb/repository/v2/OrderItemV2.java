package com.example.dynamodb.repository.v2;

import com.example.dynamodb.domain.Order;
import com.example.dynamodb.domain.OrderStatus;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.extensions.annotations.DynamoDbVersionAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AWS SDK v2 用 DynamoDB Entity (DynamoDbEnhancedClient)
 */
@DynamoDbBean
@Setter
public class OrderItemV2 {

    /** 顧客ID（Partition Key） */
    private String customerId;

    /** 注文ID（Sort Key） */
    private String orderId;

    /** 注文日時 */
    private Instant orderDate;

    /** 注文ステータス */
    private OrderStatus status;

    /** 注文合計金額 */
    private BigDecimal totalAmount;

    /** 注文明細アイテムリスト */
    private List<OrderLineItemV2> items;

    /** 楽観的ロック用バージョン番号 */
    private Long version;

    /** 最終更新日時 */
    private Instant updatedAt;

    /**
     * デフォルトコンストラクタ。
     */
    public OrderItemV2() {
        this.items = new ArrayList<>();
    }

    /**
     * ドメインモデル {@link Order} から SDK v2 用エンティティを生成します。
     *
     * @param domain
     *            変換元のドメイン注文モデル
     * @return 変換後の {@link OrderItemV2} インスタンス（引数が null の場合は null）
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
     * SDK v2 用エンティティからドメインモデル {@link Order} へ変換します。
     *
     * @return 変換後のドメイン注文モデル
     */
    public Order toDomain() {
        return new Order(customerId, orderId, orderDate, status, totalAmount,
                items != null
                        ? items.stream().map(OrderLineItemV2::toDomain).collect(Collectors.toList())
                        : Collections.emptyList(),
                version, updatedAt);
    }

    /**
     * 顧客ID（Partition Key）を取得します。
     *
     * @return 顧客ID
     */
    @DynamoDbPartitionKey
    @DynamoDbAttribute("customerId")
    public String getCustomerId() {
        return customerId;
    }

    /**
     * 注文ID（Sort Key）を取得します。
     *
     * @return 注文ID
     */
    @DynamoDbSortKey
    @DynamoDbAttribute("orderId")
    public String getOrderId() {
        return orderId;
    }

    /**
     * 注文日時を取得します。GSI の Sort Key としても機能します。
     *
     * @return 注文日時
     */
    @DynamoDbSecondarySortKey(indexNames = "status-orderDate-index")
    @DynamoDbAttribute("orderDate")
    public Instant getOrderDate() {
        return orderDate;
    }

    /**
     * 注文ステータスを取得します。GSI の Partition Key としても機能します。
     *
     * @return 注文ステータス
     */
    @DynamoDbSecondaryPartitionKey(indexNames = "status-orderDate-index")
    @DynamoDbAttribute("status")
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * 注文合計金額を取得します。
     *
     * @return 注文合計金額
     */
    @DynamoDbAttribute("totalAmount")
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * 注文明細アイテムリストを取得します。
     *
     * @return 注文明細アイテムリスト
     */
    @DynamoDbAttribute("items")
    public List<OrderLineItemV2> getItems() {
        return items;
    }

    /**
     * 注文明細アイテムリストを設定します。
     *
     * @param items
     *            注文明細アイテムリスト
     */
    public void setItems(List<OrderLineItemV2> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    /**
     * 楽観的ロック用のバージョン番号を取得します。
     *
     * @return バージョン番号
     */
    @DynamoDbVersionAttribute
    @DynamoDbAttribute("version")
    public Long getVersion() {
        return version;
    }

    /**
     * 最終更新日時を取得します。
     *
     * @return 最終更新日時
     */
    @DynamoDbAttribute("updatedAt")
    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
