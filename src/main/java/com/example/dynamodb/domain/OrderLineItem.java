package com.example.dynamodb.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Order line item value object (independent of AWS SDK).
 */
@Data
@AllArgsConstructor
@Builder
public class OrderLineItem {

    /**
     * Default constructor.
     */
    public OrderLineItem() {
    }

    /** Item identifier */
    private String itemId;

    /** Item name */
    private String itemName;

    /** Quantity ordered */
    private int quantity;

    /** Unit price */
    private BigDecimal unitPrice;

    /**
     * Calculates the subtotal (unitPrice * quantity).
     *
     * @return Subtotal amount (0 if unitPrice is null)
     */
    public BigDecimal getSubtotal() {
        if (unitPrice == null) {
            return BigDecimal.ZERO;
        }
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
