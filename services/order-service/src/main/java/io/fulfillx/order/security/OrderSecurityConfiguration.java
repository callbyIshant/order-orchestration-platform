package io.fulfillx.order.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fulfillx.order.api.OrderApiErrors;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;

@Configuration
public class OrderSecurityConfiguration {
    private final ObjectMapper mapper;
    public OrderSecurityConfiguration(ObjectMapper mapper) { this.mapper = mapper; }

    @Bean
    JwtDecoder orderJwtDecoder(OrderOidcProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(properties.jwkSetUri()).build();
        OAuth2TokenValidator<Jwt> issuer = JwtValidators.createDefaultWithIssuer(properties.issuerUri());
        OAuth2TokenValidator<Jwt> audienceAndTokenUse = jwt -> {
            boolean cognito = "cognito".equalsIgnoreCase(properties.provider());
            boolean audienceMatches = jwt.getAudience().contains(properties.audience())
                    || (cognito && properties.audience().equals(jwt.getClaimAsString("client_id")));
            boolean tokenUseMatches = cognito ? "access".equals(jwt.getClaimAsString("token_use")) : "keycloak".equalsIgnoreCase(properties.provider()) && "Bearer".equals(jwt.getClaimAsString("typ"));
            return audienceMatches && tokenUseMatches ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token audience or token type is invalid.", null));
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(issuer, audienceAndTokenUse));
        return decoder;
    }

    @Bean
    Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>();
            addRoles(authorities, jwt.getClaim("roles"));
            addRoles(authorities, jwt.getClaim("cognito:groups"));
            Map<String, Object> realmAccess = jwt.getClaim("realm_access");
            if (realmAccess != null) addRoles(authorities, realmAccess.get("roles"));
            return authorities;
        });
        return converter;
    }

    private static void addRoles(Collection<GrantedAuthority> authorities, Object roles) {
        if (roles instanceof Collection<?> values) {
            for (Object value : values) if (value instanceof String role && Set.of("CUSTOMER", "OPERATIONS", "ADMIN").contains(role.toUpperCase(Locale.ROOT)))
                authorities.add(new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)));
        }
    }

    @Bean
    SecurityFilterChain orderSecurity(HttpSecurity http, Converter<Jwt, ? extends AbstractAuthenticationToken> jwtAuthenticationConverter) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, ex) -> writeProblem(request, response, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "A valid customer access token is required."))
                        .accessDeniedHandler((request, response, ex) -> writeProblem(request, response, HttpStatus.FORBIDDEN, "FORBIDDEN", "The caller does not have the CUSTOMER role.")))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole("ADMIN")
                        .requestMatchers("/api/v1/orders", "/api/v1/orders/**").hasRole("CUSTOMER")
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .build();
    }

    private void writeProblem(HttpServletRequest request, HttpServletResponse response, HttpStatus status, String code, String detail) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        mapper.writeValue(response.getOutputStream(), OrderApiErrors.problem(status, code, detail, request));
    }
}

