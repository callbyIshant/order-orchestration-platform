package io.fulfillx.catalog.application;

public final class DuplicateSkuException extends RuntimeException {

    public DuplicateSkuException(String sku) {
        super("A product with SKU " + sku + " already exists.");
    }
}
