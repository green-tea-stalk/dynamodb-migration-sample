package com.example.dynamodb;

import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.v1.V1DynamoDbOrderRepository;
import org.junit.jupiter.api.DisplayName;

/**
 * Contract test implementation for AWS SDK v1 (DynamoDBMapper).
 */
@DisplayName("AWS SDK v1 (DynamoDBMapper) OrderRepository Tests")
public class V1OrderRepositoryTest extends OrderRepositoryContractTest {

    /**
     * Creates an instance of {@link V1DynamoDbOrderRepository} under test.
     *
     * @return {@link V1DynamoDbOrderRepository} instance
     */
    @Override
    protected OrderRepository createRepository() {
        return new V1DynamoDbOrderRepository(v1Client);
    }
}
