package com.example.dynamodb;

import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.v2.V2DynamoDbEnhancedOrderRepository;
import org.junit.jupiter.api.DisplayName;

/**
 * Contract test implementation for AWS SDK v2 (DynamoDbEnhancedClient).
 */
@DisplayName("AWS SDK v2 (DynamoDbEnhancedClient) OrderRepository Tests")
public class V2OrderRepositoryTest extends OrderRepositoryContractTest {

    /**
     * Creates an instance of {@link V2DynamoDbEnhancedOrderRepository} under test.
     *
     * @return {@link V2DynamoDbEnhancedOrderRepository} instance
     */
    @Override
    protected OrderRepository createRepository() {
        return new V2DynamoDbEnhancedOrderRepository(v2EnhancedClient);
    }
}
