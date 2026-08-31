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
 * Configuration helper for creating AWS SDK v2 {@link DynamoDbClient} and
 * {@link DynamoDbEnhancedClient}.
 */
public class DynamoDbV2Config {

    private DynamoDbV2Config() {
        // Prevent instantiation
    }

    /**
     * Creates an AWS SDK v2 {@link DynamoDbClient}.
     *
     * @param endpoint
     *            Custom endpoint URI (e.g. for DynamoDB Local, null for AWS
     *            production)
     * @param region
     *            AWS region name (defaults to {@link Region#US_EAST_1} if null)
     * @param accessKey
     *            AWS access key ID (null to use default credentials provider chain)
     * @param secretKey
     *            AWS secret access key (null to use default credentials provider
     *            chain)
     * @return Configured {@link DynamoDbClient} instance
     */
    public static DynamoDbClient createClient(URI endpoint, String region, String accessKey, String secretKey) {
        DynamoDbClientBuilder builder = DynamoDbClient.builder().httpClient(UrlConnectionHttpClient.create());

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
                    StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        }

        return builder.build();
    }

    /**
     * Creates a high-level mapper {@link DynamoDbEnhancedClient} wrapping a
     * {@link DynamoDbClient}.
     *
     * @param dynamoDbClient
     *            Low-level {@link DynamoDbClient} instance
     * @return Configured {@link DynamoDbEnhancedClient} instance
     */
    public static DynamoDbEnhancedClient createEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).build();
    }

    /**
     * Creates a high-level mapper {@link DynamoDbEnhancedClient} specifically for
     * batch operations. This client clears all default extensions (including
     * VersionedRecordExtension) so that batch operations on versioned items do not
     * fail with IllegalArgumentException.
     *
     * @param dynamoDbClient
     *            Low-level {@link DynamoDbClient} instance
     * @return Configured {@link DynamoDbEnhancedClient} instance without extensions
     */
    public static DynamoDbEnhancedClient createBatchEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder().dynamoDbClient(dynamoDbClient).extensions().build();
    }
}
