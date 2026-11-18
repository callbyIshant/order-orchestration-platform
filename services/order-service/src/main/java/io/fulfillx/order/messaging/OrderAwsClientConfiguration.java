package io.fulfillx.order.messaging;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;
import java.net.URI;

@Configuration
public class OrderAwsClientConfiguration {
    private static StaticCredentialsProvider credentials(OrderAwsProperties p){return StaticCredentialsProvider.create(AwsBasicCredentials.create(p.accessKeyId().isBlank()?"test":p.accessKeyId(),p.secretAccessKey().isBlank()?"test":p.secretAccessKey()));}
    @Bean SqsClient orderSqs(OrderAwsProperties p){var b=SqsClient.builder().region(Region.of(p.region())).credentialsProvider(credentials(p));if(p.endpointUrl()!=null&&!p.endpointUrl().isBlank())b.endpointOverride(URI.create(p.endpointUrl()));return b.build();}
    @Bean SnsClient orderSns(OrderAwsProperties p){var b=SnsClient.builder().region(Region.of(p.region())).credentialsProvider(credentials(p));if(p.endpointUrl()!=null&&!p.endpointUrl().isBlank())b.endpointOverride(URI.create(p.endpointUrl()));return b.build();}
}
