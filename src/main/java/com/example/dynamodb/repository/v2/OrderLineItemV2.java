package com.example.dynamodb.repository.v2;

import com.example.dynamodb.domain.OrderLineItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.math.BigDecimal;

/**
 * AWS SDK v2 用 注文明細 DTO (DynamoDbEnhancedClient)
 */
@DynamoDbBean
@Data
@AllArgsConstructor
public class OrderLineItemV2 {

    /**
     * デフォルトコンストラクタ。
     */
    public OrderLineItemV2() {
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
     * ドメインモデル {@link OrderLineItem} から SDK v2 用 DTO を生成します。
     *
     * @param domain
     *            変換元のドメイン明細モデル
     * @return 変換後の {@link OrderLineItemV2} インスタンス（引数が null の場合は null）
     */
    public static OrderLineItemV2 fromDomain(OrderLineItem domain) {
        if (domain == null) {
            return null;
        }
        return new OrderLineItemV2(domain.getItemId(), domain.getItemName(), domain.getQuantity(),
                domain.getUnitPrice());
    }

    /**
     * SDK v2 用 DTO からドメインモデル {@link OrderLineItem} へ変換します。
     *
     * @return 変換後のドメイン明細モデル
     */
    public OrderLineItem toDomain() {
        return new OrderLineItem(itemId, itemName, quantity, unitPrice);
    }
}
