package io.fulfillx.catalog.application;

import java.util.UUID;

public final class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(UUID categoryId) {
        super("Category " + categoryId + " was not found.");
    }
}
