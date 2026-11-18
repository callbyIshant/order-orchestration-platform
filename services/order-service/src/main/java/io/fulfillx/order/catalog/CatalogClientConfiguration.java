package io.fulfillx.order.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration
public class CatalogClientConfiguration {
    @Bean
    RestClient catalogRestClient(CatalogProperties properties, RestClient.Builder builder) {
        HttpClient client = HttpClient.newBuilder().connectTimeout(properties.connectTimeout()).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.responseTimeout());
        return builder.baseUrl(properties.baseUrl()).requestFactory(factory).build();
    }
}
