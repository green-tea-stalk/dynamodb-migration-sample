package com.example.dynamodb.domain;

/**
 * 注文ステータスを表す列挙型
 */
public enum OrderStatus {
    /** 注文作成済み */
    CREATED,
    /** 決済完了 */
    PAID,
    /** 出荷済み */
    SHIPPED,
    /** 配達完了 */
    DELIVERED,
    /** キャンセル済み */
    CANCELLED
}
