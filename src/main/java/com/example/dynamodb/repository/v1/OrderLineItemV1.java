package com.example.dynamodb.repository.v1;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.example.dynamodb.domain.OrderLineItem;
import lombok.*;

import java.math.BigDecimal;

/**
 * AWS SDK v1 用 注文明細 DTO
 */
@DynamoDBDocument
@Data
@AllArgsConstructor
public class OrderLineItemV1 {

    /**
     * デフォルトコンストラクタ。
     */
    public OrderLineItemV1() {
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
     * ドメインモデル {@link OrderLineItem} から SDK v1 用 DTO を生成します。
     *
     * @param domain 変換元のドメイン明細モデル
     * @return 変換後の {@link OrderLineItemV1} インスタンス（引数が null の場合は null）
     */
    public static OrderLineItemV1 fromDomain(OrderLineItem domain) {
        if (domain == null) {
            return null;
        }
        return new OrderLineItemV1(
                domain.getItemId(),
                domain.getItemName(),
                domain.getQuantity(),
                domain.getUnitPrice()
        );
    }

    /**
     * SDK v1 用 DTO からドメインモデル {@link OrderLineItem} へ変換します。
     *
     * @return 変換後のドメイン明細モデル
     */
    public OrderLineItem toDomain() {
        return new OrderLineItem(itemId, itemName, quantity, unitPrice);
    }
}
