package com.example.dynamodb.config;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

/**
 * AWS SDK v2 DynamoDbClient / DynamoDbEnhancedClient クライアント設定ヘルパー
 */
public class DynamoDbV2Config {

    private DynamoDbV2Config() {
        // インスタンス化防止
    }

    /**
     * AWS SDK v2 の {@link DynamoDbClient} クライアントを生成します。
     *
     * @param endpoint  カスタムエンドポイントURI（DynamoDB Local 接続時などに指定、AWS実環境の場合は null）
     * @param region    AWS リージョン名（null の場合はデフォルトで {@link Region#US_EAST_1} を使用）
     * @param accessKey AWS アクセスキー（null の場合はデフォルトの認証情報プロバイダチェーンを使用）
     * @param secretKey AWS シークレットアクセスキー（null の場合はデフォルトの認証情報プロバイダチェーンを使用）
     * @return 設定された {@link DynamoDbClient} クライアントインスタンス
     */
    public static DynamoDbClient createClient(URI endpoint, String region, String accessKey, String secretKey) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .httpClient(UrlConnectionHttpClient.create());

        if (endpoint != null) {
            builder.endpointOverride(endpoint);
        }

        if (region != null) {
            builder.region(Region.of(region));
        } else {
            builder.region(Region.US_EAST_1);
        }

        if (accessKey != null && secretKey != null) {
            builder.credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey))
            );
        }

        return builder.build();
    }

    /**
     * {@link DynamoDbClient} をラップした高レベルマッパー {@link DynamoDbEnhancedClient} を生成します。
     *
     * @param dynamoDbClient 低レベルの {@link DynamoDbClient} インスタンス
     * @return 設定された {@link DynamoDbEnhancedClient} インスタンス
     */
    public static DynamoDbEnhancedClient createEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
