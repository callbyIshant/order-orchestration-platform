package io.fulfillx.catalog.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {
    private final Category category = new Category(UUID.randomUUID(), "Home", "home");

    @Test void normalizesSkuAndPrice() {
        Product product = new Product(UUID.randomUUID(), " mug-01 ", " Mug ", " ", new BigDecimal("12.5"), category, true);
        assertEquals("MUG-01", product.getSku());
        assertEquals("Mug", product.getName());
        assertNull(product.getDescription());
        assertEquals(new BigDecimal("12.50"), product.getUnitPriceInr());
    }

    @Test void rejectsNegativeOrOverPrecisionPrice() {
        assertEquals("INVALID_UNIT_PRICE", assertThrows(CatalogDomainException.class,
                () -> Product.normalizePrice(new BigDecimal("-0.01"))).getCode());
        assertThrows(CatalogDomainException.class, () -> Product.normalizePrice(new BigDecimal("1.001")));
    }

    @Test void patchCanClearDescriptionAndKeepSkuImmutable() {
        Product product = new Product(UUID.randomUUID(), "MUG-01", "Mug", "Ceramic", new BigDecimal("12.00"), category, true);
        product.update(null, "", null, null, false);
        assertNull(product.getDescription());
        assertFalse(product.isActive());
        assertEquals("MUG-01", product.getSku());
    }
}
