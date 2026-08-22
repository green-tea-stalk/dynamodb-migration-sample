package com.example.dynamodb.repository.v1;

import com.amazonaws.services.dynamodbv2.datamodeling.*;
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
 * AWS SDK v1 用 DynamoDB Entity (DynamoDBMapper)
 */
@DynamoDBTable(tableName = "orders")
@Setter
public class OrderItemV1 {

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
    private List<OrderLineItemV1> items;

    /** 楽観的ロック用バージョン番号 */
    private Long version;

    /** 最終更新日時 */
    private Instant updatedAt;

    /**
     * デフォルトコンストラクタ。
     */
    public OrderItemV1() {
        this.items = new ArrayList<>();
    }

    /**
     * ドメインモデル {@link Order} から SDK v1 用エンティティを生成します。
     *
     * @param domain 変換元のドメイン注文モデル
     * @return 変換後の {@link OrderItemV1} インスタンス（引数が null の場合は null）
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
            item.setItems(domain.getItems().stream()
                    .map(OrderLineItemV1::fromDomain)
                    .collect(Collectors.toList()));
        }
        item.setVersion(domain.getVersion());
        item.setUpdatedAt(domain.getUpdatedAt());
        return item;
    }

    /**
     * SDK v1 用エンティティからドメインモデル {@link Order} へ変換します。
     *
     * @return 変換後のドメイン注文モデル
     */
    public Order toDomain() {
        return new Order(
                customerId,
                orderId,
                orderDate,
                status,
                totalAmount,
                items != null ? items.stream().map(OrderLineItemV1::toDomain).collect(Collectors.toList()) : Collections.emptyList(),
                version,
                updatedAt
        );
    }

    /**
     * 顧客ID（Partition Key）を取得します。
     *
     * @return 顧客ID
     */
    @DynamoDBHashKey(attributeName = "customerId")
    public String getCustomerId() {
        return customerId;
    }

    /**
     * 注文ID（Sort Key）を取得します。
     *
     * @return 注文ID
     */
    @DynamoDBRangeKey(attributeName = "orderId")
    public String getOrderId() {
        return orderId;
    }

    /**
     * 注文日時を取得します。GSI の Sort Key としても機能します。
     *
     * @return 注文日時
     */
    @DynamoDBTypeConverted(converter = InstantTypeConverter.class)
    @DynamoDBIndexRangeKey(globalSecondaryIndexName = "status-orderDate-index", attributeName = "orderDate")
    @DynamoDBAttribute(attributeName = "orderDate")
    public Instant getOrderDate() {
        return orderDate;
    }

    /**
     * 注文ステータスを取得します。GSI の Partition Key としても機能します。
     *
     * @return 注文ステータス
     */
    @DynamoDBTypeConvertedEnum
    @DynamoDBIndexHashKey(globalSecondaryIndexName = "status-orderDate-index", attributeName = "status")
    @DynamoDBAttribute(attributeName = "status")
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * 注文合計金額を取得します。
     *
     * @return 注文合計金額
     */
    @DynamoDBAttribute(attributeName = "totalAmount")
    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    /**
     * 注文明細アイテムリストを取得します。
     *
     * @return 注文明細アイテムリスト
     */
    @DynamoDBAttribute(attributeName = "items")
    public List<OrderLineItemV1> getItems() {
        return items;
    }

    /**
     * 注文明細アイテムリストを設定します。
     *
     * @param items 注文明細アイテムリスト
     */
    public void setItems(List<OrderLineItemV1> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    /**
     * 楽観的ロック用のバージョン番号を取得します。
     *
     * @return バージョン番号
     */
    @DynamoDBVersionAttribute(attributeName = "version")
    public Long getVersion() {
        return version;
    }

    /**
     * 最終更新日時を取得します。
     *
     * @return 最終更新日時
     */
    @DynamoDBTypeConverted(converter = InstantTypeConverter.class)
    @DynamoDBAttribute(attributeName = "updatedAt")
    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /**
     * {@link Instant} と ISO-8601 文字列形式を相互変換するカスタムコンバーター
     */
    public static class InstantTypeConverter implements DynamoDBTypeConverter<String, Instant> {

        /**
         * デフォルトコンストラクタ。
         */
        public InstantTypeConverter() {
        }

        /**
         * {@link Instant} を ISO-8601 文字列に変換します。
         *
         * @param object 変換対象の {@link Instant}
         * @return ISO-8601 形式の文字列（null の場合は null）
         */
        @Override
        public String convert(Instant object) {
            return object != null ? object.toString() : null;
        }

        /**
         * ISO-8601 文字列を {@link Instant} に変換します。
         *
         * @param object 変換対象の ISO-8601 文字列
         * @return 復元された {@link Instant}（null の場合は null）
         */
        @Override
        public Instant unconvert(String object) {
            return object != null ? Instant.parse(object) : null;
        }
    }
}
