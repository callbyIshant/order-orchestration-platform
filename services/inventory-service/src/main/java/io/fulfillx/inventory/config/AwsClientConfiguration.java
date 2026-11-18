package io.fulfillx.inventory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

@Configuration
public class AwsClientConfiguration {
    @Bean(destroyMethod = "close")
    SnsClient snsClient(@Value("${aws.region}") String region, @Value("${aws.endpoint-url:}") String endpoint,
                        @Value("${aws.access-key-id:}") String accessKey, @Value("${aws.secret-access-key:}") String secretKey) {
        var builder = SnsClient.builder().region(Region.of(region)).credentialsProvider(credentials(accessKey, secretKey));
        if (endpoint != null && !endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    @Bean(destroyMethod = "close")
    SqsClient sqsClient(@Value("${aws.region}") String region, @Value("${aws.endpoint-url:}") String endpoint,
                        @Value("${aws.access-key-id:}") String accessKey, @Value("${aws.secret-access-key:}") String secretKey) {
        var builder = SqsClient.builder().region(Region.of(region)).credentialsProvider(credentials(accessKey, secretKey));
        if (endpoint != null && !endpoint.isBlank()) builder.endpointOverride(URI.create(endpoint));
        return builder.build();
    }

    private static software.amazon.awssdk.auth.credentials.AwsCredentialsProvider credentials(String accessKey, String secretKey) {
        boolean hasAccessKey = accessKey != null && !accessKey.isBlank();
        boolean hasSecretKey = secretKey != null && !secretKey.isBlank();
        if (hasAccessKey != hasSecretKey) throw new IllegalStateException("AWS_ACCESS_KEY_ID and AWS_SECRET_ACCESS_KEY must be configured together.");
        if (hasAccessKey) return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
        return DefaultCredentialsProvider.builder().build();
    }
}
