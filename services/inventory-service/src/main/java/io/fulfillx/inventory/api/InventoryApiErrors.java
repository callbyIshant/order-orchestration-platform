package io.fulfillx.inventory.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

public final class InventoryApiErrors {
    public static final String CORRELATION_ID = "inventory.correlationId";
    private InventoryApiErrors() { }
    public static ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://fulfillx.local/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(code.replace('_', ' '));
        problem.setProperty("code", code);
        problem.setProperty("path", request.getRequestURI());
        Object correlationId = request.getAttribute(CORRELATION_ID);
        if (correlationId != null) problem.setProperty("correlationId", correlationId.toString());
        return problem;
    }
}
