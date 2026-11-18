package io.fulfillx.catalog.api;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import java.net.URI;

public final class ApiErrors {
    public static final String CORRELATION_ID = "catalog.correlationId";
    private ApiErrors() { }

    public static ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://fulfillx.local/problems/" + code.toLowerCase().replace('_', '-')));
        problem.setTitle(code.replace('_', ' '));
        problem.setProperty("code", code);
        problem.setProperty("path", request.getRequestURI());
        Object correlation = request.getAttribute(CORRELATION_ID);
        if (correlation != null) problem.setProperty("correlationId", correlation.toString());
        return problem;
    }
}
