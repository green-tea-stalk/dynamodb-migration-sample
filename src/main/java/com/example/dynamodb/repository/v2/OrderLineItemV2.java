package com.example.dynamodb.repository.v2;

import com.example.dynamodb.domain.OrderLineItem;
import lombok.AllArgsConstructor;
import lombok.Data;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.math.BigDecimal;

/**
 * Order line item DTO for AWS SDK v2 (DynamoDbEnhancedClient).
 */
@DynamoDbBean
@Data
@AllArgsConstructor
public class OrderLineItemV2 {

    /**
     * Default constructor.
     */
    public OrderLineItemV2() {
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
     * Converts a domain model {@link OrderLineItem} to an SDK v2 DTO.
     *
     * @param domain
     *            Source domain line item
     * @return Converted {@link OrderLineItemV2} instance (null if input is null)
     */
    public static OrderLineItemV2 fromDomain(OrderLineItem domain) {
        if (domain == null) {
            return null;
        }
        return new OrderLineItemV2(domain.getItemId(), domain.getItemName(), domain.getQuantity(),
                domain.getUnitPrice());
    }

    /**
     * Converts this SDK v2 DTO to a domain model {@link OrderLineItem}.
     *
     * @return Converted domain line item
     */
    public OrderLineItem toDomain() {
        return new OrderLineItem(itemId, itemName, quantity, unitPrice);
    }
}
