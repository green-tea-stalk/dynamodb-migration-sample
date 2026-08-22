package com.example.dynamodb;

import com.example.dynamodb.repository.OrderRepository;
import com.example.dynamodb.repository.v1.V1DynamoDbOrderRepository;
import org.junit.jupiter.api.DisplayName;

/**
 * AWS SDK v1 (DynamoDBMapper) 実装に対する契約テスト
 */
@DisplayName("AWS SDK v1 (DynamoDBMapper) OrderRepository テスト")
public class V1OrderRepositoryTest extends OrderRepositoryContractTest {

    /**
     * {@link V1DynamoDbOrderRepository} のインスタンスを生成して返します。
     *
     * @return {@link V1DynamoDbOrderRepository} インスタンス
     */
    @Override
    protected OrderRepository createRepository() {
        return new V1DynamoDbOrderRepository(v1Client);
    }
}
