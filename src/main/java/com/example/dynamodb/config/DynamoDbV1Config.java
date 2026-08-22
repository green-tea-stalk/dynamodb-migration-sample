package com.example.dynamodb.config;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

import java.net.URI;

/**
 * Configuration helper for creating AWS SDK v1 {@link AmazonDynamoDB} client.
 */
public class DynamoDbV1Config {

    private DynamoDbV1Config() {
        // Prevent instantiation
    }

    /**
     * Creates an AWS SDK v1 {@link AmazonDynamoDB} client.
     *
     * @param endpoint
     *            Custom endpoint URI (e.g. for DynamoDB Local, null for AWS
     *            production)
     * @param region
     *            AWS region name (defaults to "us-east-1" if null)
     * @param accessKey
     *            AWS access key ID (null to use default credentials provider chain)
     * @param secretKey
     *            AWS secret access key (null to use default credentials provider
     *            chain)
     * @return Configured {@link AmazonDynamoDB} client instance
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
