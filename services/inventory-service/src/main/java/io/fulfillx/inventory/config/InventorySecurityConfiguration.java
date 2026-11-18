package io.fulfillx.inventory.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;
import java.util.Set;

@Configuration
public class InventorySecurityConfiguration {
    @Bean
    JwtDecoder inventoryJwtDecoder(@Value("${fulfillx.oidc.provider}") String provider,
                                   @Value("${fulfillx.oidc.issuer-uri}") String issuerUri,
                                   @Value("${fulfillx.oidc.jwk-set-uri}") String jwkSetUri,
                                   @Value("${fulfillx.oidc.audience}") String audience) {
        if (issuerUri.isBlank() || jwkSetUri.isBlank() || audience.isBlank()) {
            throw new IllegalStateException("OIDC issuer, JWKS URI, and audience must be configured.");
        }
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        OAuth2TokenValidator<Jwt> audienceAndType = jwt -> {
            boolean cognito = "cognito".equalsIgnoreCase(provider);
            boolean audienceMatches = jwt.getAudience().contains(audience)
                    || (cognito && audience.equals(jwt.getClaimAsString("client_id")));
            boolean accessToken = cognito ? "access".equals(jwt.getClaimAsString("token_use")) : "keycloak".equalsIgnoreCase(provider) && "Bearer".equals(jwt.getClaimAsString("typ"));
            return audienceMatches && accessToken ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token audience or token type is invalid.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuerUri), audienceAndType));
        return decoder;
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> inventoryJwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            ArrayList<GrantedAuthority> authorities = new ArrayList<>();
            addRoles(authorities, jwt.getClaim("roles"));
            addRoles(authorities, jwt.getClaim("cognito:groups"));
            var realmAccess = jwt.getClaimAsMap("realm_access");
            if (realmAccess != null) addRoles(authorities, realmAccess.get("roles"));
            return authorities;
        });
        return converter;
    }

    @Bean
    SecurityFilterChain inventorySecurity(HttpSecurity http,
            Converter<Jwt, ? extends AbstractAuthenticationToken> inventoryJwtAuthenticationConverter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/inventory/**").hasAnyRole("OPERATIONS", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/v1/inventory/**").hasAnyRole("OPERATIONS", "ADMIN")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(inventoryJwtAuthenticationConverter)))
                .build();
    }

    private static void addRoles(Collection<GrantedAuthority> authorities, Object roles) {
        if (roles instanceof Collection<?> values) {
            for (Object value : values) {
                if (value instanceof String role && Set.of("CUSTOMER", "OPERATIONS", "ADMIN").contains(role.toUpperCase(Locale.ROOT))) {
                    authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
                }
            }
        }
    }
}

