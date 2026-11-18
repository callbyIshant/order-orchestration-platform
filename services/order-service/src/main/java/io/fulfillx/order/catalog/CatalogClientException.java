package io.fulfillx.order.catalog;

public class CatalogClientException extends RuntimeException {
    public enum Kind { NOT_ORDERABLE, UNAVAILABLE }
    private final Kind kind;
    public CatalogClientException(Kind kind, String message, Throwable cause) { super(message, cause); this.kind = kind; }
    public Kind getKind() { return kind; }
}
