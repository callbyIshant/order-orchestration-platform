package io.fulfillx.order.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("fulfillx.oidc")
public record OrderOidcProperties(String provider, String issuerUri, String jwkSetUri, String audience) { }
