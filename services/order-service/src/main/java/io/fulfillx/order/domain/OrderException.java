package io.fulfillx.order.domain;

public class OrderException extends RuntimeException {
    private final String code;
    private final int status;
    public OrderException(String code, String message, int status) { super(message); this.code = code; this.status = status; }
    public String getCode() { return code; }
    public int getStatus() { return status; }
}
