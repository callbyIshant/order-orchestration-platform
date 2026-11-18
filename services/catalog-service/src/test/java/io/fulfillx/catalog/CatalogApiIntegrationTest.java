package io.fulfillx.catalog;

import io.fulfillx.catalog.persistence.ProductRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("local")
class CatalogApiIntegrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17-alpine");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired MockMvc mvc;
    @Autowired ProductRepository products;

    private static org.springframework.test.web.servlet.request.RequestPostProcessor operations() {
        return jwt().jwt(token -> token.subject("operations-user"))
                .authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATIONS"));
    }

    @Test void flywaySeedsCatalogAndFiltersAndPaginates() throws Exception {
        org.junit.jupiter.api.Assertions.assertEquals(5, products.count());
        mvc.perform(get("/api/v1/products").param("active", "true").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(2))
                .andExpect(jsonPath("$.totalElements").value(4)).andExpect(jsonPath("$.content[0].currency").value("INR"));
        mvc.perform(get("/api/v1/products").param("active", "false"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mvc.perform(get("/api/v1/products").param("categoryId", "10000000-0000-0000-0000-000000000001"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test void operationsCanCreateAndPatchButWritesRequireAuthentication() throws Exception {
        String body = """
                {"sku":"TEST-MUG","name":"Test Mug","description":"ceramic","unitPrice":12.50,"categoryId":"10000000-0000-0000-0000-000000000001"}
                """;
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/products").with(operations())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.currency").value("INR"));
        mvc.perform(post("/api/v1/products").with(operations())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_SKU"));
    }

    @Test void invalidRequestsAndMissingProductsUseProblemDetails() throws Exception {
        mvc.perform(post("/api/v1/products").with(operations())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"\"}"))
                .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith("application/problem+json"))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
        mvc.perform(get("/api/v1/products/ffffffff-ffff-ffff-ffff-ffffffffffff").header("X-Correlation-ID", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.correlationId").value("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                .andExpect(header().string("X-Correlation-ID", "aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"));
        mvc.perform(get("/api/v1/products").param("size", "101"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.detail", containsString("Page size")));
    }

    @Test void operationsCanPatchProductAndClearDescription() throws Exception {
        mvc.perform(patch("/api/v1/products/20000000-0000-0000-0000-000000000001")
                        .with(operations())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"description\":\"\",\"active\":false}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.description").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.active").value(false)).andExpect(jsonPath("$.sku").value("MUG-001"));
    }

    @Test void rejectsInvalidBearerWrongRoleAndProtectsOperationalMetrics() throws Exception {
        String body = "{\"sku\":\"SECURE-MUG\",\"name\":\"Secure Mug\",\"unitPrice\":10.00,\"categoryId\":\"10000000-0000-0000-0000-000000000001\"}";
        mvc.perform(post("/api/v1/products").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/products").header("Authorization", "Bearer not-a-jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/products").with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_CUSTOMER")))
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_OPERATIONS"))))
                .andExpect(status().isForbidden());
        mvc.perform(get("/actuator/metrics").with(jwt().authorities(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }
}
