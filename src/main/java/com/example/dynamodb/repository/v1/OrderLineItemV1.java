package com.example.dynamodb.repository.v1;

import com.amazonaws.services.dynamodbv2.datamodeling.DynamoDBDocument;
import com.example.dynamodb.domain.OrderLineItem;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Order line item DTO for AWS SDK v1.
 */
@DynamoDBDocument
@Data
@AllArgsConstructor
public class OrderLineItemV1 {

    /**
     * Default constructor.
     */
    public OrderLineItemV1() {
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
     * Converts a domain model {@link OrderLineItem} to an SDK v1 DTO.
     *
     * @param domain
     *            Source domain line item
     * @return Converted {@link OrderLineItemV1} instance (null if input is null)
     */
    public static OrderLineItemV1 fromDomain(OrderLineItem domain) {
        if (domain == null) {
            return null;
        }
        return new OrderLineItemV1(domain.getItemId(), domain.getItemName(), domain.getQuantity(),
                domain.getUnitPrice());
    }

    /**
     * Converts this SDK v1 DTO to a domain model {@link OrderLineItem}.
     *
     * @return Converted domain line item
     */
    public OrderLineItem toDomain() {
        return new OrderLineItem(itemId, itemName, quantity, unitPrice);
    }
}
