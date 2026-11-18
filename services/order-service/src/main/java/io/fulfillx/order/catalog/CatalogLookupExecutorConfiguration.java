package io.fulfillx.order.catalog;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
public class CatalogLookupExecutorConfiguration {
    @Bean("catalogLookupExecutor")
    Executor catalogLookupExecutor(CatalogProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.maxConcurrentLookups());
        executor.setMaxPoolSize(properties.maxConcurrentLookups());
        executor.setQueueCapacity(50);
        executor.setThreadNamePrefix("catalog-lookup-");
        executor.initialize();
        return executor;
    }
}
