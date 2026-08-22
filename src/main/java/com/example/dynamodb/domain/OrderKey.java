package com.example.dynamodb.domain;

/**
 * 注文の複合キー（Partition Key + Sort Key）を表すイミュータブルなキーレコード
 *
 * @param customerId 顧客ID（Partition Key）
 * @param orderId    注文ID（Sort Key）
 */
public record OrderKey(String customerId, String orderId) {
}
