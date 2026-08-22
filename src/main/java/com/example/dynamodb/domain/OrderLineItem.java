package com.example.dynamodb.domain;

import lombok.*;

import java.math.BigDecimal;

/**
 * 注文明細アイテム（AWS SDK 非依存）
 */
@Data
@AllArgsConstructor
@Builder
public class OrderLineItem {

    /**
     * デフォルトコンストラクタ。
     */
    public OrderLineItem() {
    }

    /** 商品ID */
    private String itemId;

    /** 商品名 */
    private String itemName;

    /** 数量 */
    private int quantity;

    /** 単価 */
    private BigDecimal unitPrice;

    /**
     * 小計（単価 × 数量）を算出します。
     *
     * @return 小計金額（単価が null の場合は 0）
     */
    public BigDecimal getSubtotal() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
