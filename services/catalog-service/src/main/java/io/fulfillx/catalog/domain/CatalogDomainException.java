package io.fulfillx.catalog.domain;

public final class CatalogDomainException extends RuntimeException {

    private final String errorCode;

    public CatalogDomainException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }

    public String getCode() { return errorCode; }
}
