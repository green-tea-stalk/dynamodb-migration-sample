package com.example.dynamodb.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

import java.net.URI;

/**
 * AWS SDK v1 AmazonDynamoDB クライアント設定ヘルパー
 */
public class DynamoDbV1Config {

    private DynamoDbV1Config() {
        // インスタンス化防止
    }

    /**
     * AWS SDK v1 の {@link AmazonDynamoDB} クライアントを生成します。
     *
     * @param endpoint
     *            カスタムエンドポイントURI（DynamoDB Local 接続時などに指定、AWS実環境の場合は null）
     * @param region
     *            AWS リージョン名（null の場合はデフォルトで "us-east-1" を使用）
     * @param accessKey
     *            AWS アクセスキー（null の場合はデフォルトの認証情報プロバイダチェーンを使用）
     * @param secretKey
     *            AWS シークレットアクセスキー（null の場合はデフォルトの認証情報プロバイダチェーンを使用）
     * @return 設定された {@link AmazonDynamoDB} クライアントインスタンス
     */
    public static AmazonDynamoDB createClient(URI endpoint, String region, String accessKey, String secretKey) {
        AmazonDynamoDBClientBuilder builder = AmazonDynamoDBClientBuilder.standard();

        if (endpoint != null) {
            builder.withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(endpoint.toString(),
                    region != null ? region : "us-east-1"));
        } else if (region != null) {
            builder.withRegion(region);
        }

        if (accessKey != null && secretKey != null) {
            builder.withCredentials(new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey)));
        }

        return builder.build();
    }
}
