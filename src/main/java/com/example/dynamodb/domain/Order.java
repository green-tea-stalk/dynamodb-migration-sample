package com.example.dynamodb.domain;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 注文ドメインモデル（AWS SDK 非依存）
 */
@Data
@AllArgsConstructor
@Builder
public class Order {

    /**
     * デフォルトコンストラクタ。
     */
    public Order() {
        this.items = new ArrayList<>();
    }

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
    @Builder.Default
    private List<OrderLineItem> items = new ArrayList<>();

    /** 楽観的ロック用バージョン番号 */
    private Long version;

    /** 最終更新日時 */
    private Instant updatedAt;

    /**
     * 注文明細アイテムリストを取得します。
     *
     * @return 注文明細アイテムリスト（null の場合は空のリスト）
     */
    public List<OrderLineItem> getItems() {
        return items != null ? items : Collections.emptyList();
    }

    /**
     * 注文明細アイテムリストを設定します。
     *
     * @param items 注文明細アイテムリスト
     */
    public void setItems(List<OrderLineItem> items) {
        this.items = items != null ? new ArrayList<>(items) : new ArrayList<>();
    }
}
