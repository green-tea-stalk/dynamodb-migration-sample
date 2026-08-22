package com.example.dynamodb;

import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.v2.V2DynamoDbEnhancedOrderRepository;
import org.junit.jupiter.api.DisplayName;

/**
 * AWS SDK v2 (DynamoDbEnhancedClient) 実装に対する契約テスト
 */
@DisplayName("AWS SDK v2 (DynamoDbEnhancedClient) OrderRepository テスト")
public class V2OrderRepositoryTest extends OrderRepositoryContractTest {

    /**
     * {@link V2DynamoDbEnhancedOrderRepository} のインスタンスを生成して返します。
     *
     * @return {@link V2DynamoDbEnhancedOrderRepository} インスタンス
     */
    @Override
    protected OrderRepository createRepository() {
        return new V2DynamoDbEnhancedOrderRepository(v2EnhancedClient);
    }
}
