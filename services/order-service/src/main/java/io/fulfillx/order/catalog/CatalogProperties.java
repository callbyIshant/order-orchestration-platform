package io.fulfillx.order.catalog;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties("fulfillx.catalog")
public record CatalogProperties(String baseUrl, Duration connectTimeout, Duration responseTimeout,
                                Duration totalTimeout, int maxConcurrentLookups) { }
