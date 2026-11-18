package io.fulfillx.order.api;

import io.fulfillx.order.domain.OrderException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.net.URI;

public final class OrderApiErrors {
    private OrderApiErrors() { }
    public static ProblemDetail problem(HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://fulfillx.local/problems/" + code.toLowerCase()));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setInstance(URI.create(request.getRequestURI()));
        Object correlationId = request.getAttribute("fulfillx.correlationId");
        if (correlationId != null) problem.setProperty("correlationId", correlationId.toString());
        return problem;
    }

    @RestControllerAdvice
    public static class Advice {
        @ExceptionHandler(OrderException.class)
        ProblemDetail orderException(OrderException ex, HttpServletRequest request) {
            HttpStatus status = HttpStatus.valueOf(ex.getStatus());
            return problem(status, ex.getCode(), ex.getMessage(), request);
        }
        @ExceptionHandler(MethodArgumentNotValidException.class)
        ProblemDetail invalidRequest(MethodArgumentNotValidException ex, HttpServletRequest request) {
            String detail = ex.getBindingResult().getFieldErrors().stream().findFirst()
                    .map(error -> error.getField() + " " + error.getDefaultMessage()).orElse("Request validation failed.");
            return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", detail, request);
        }
        @ExceptionHandler(HttpMessageNotReadableException.class)
        ProblemDetail unreadableRequest(HttpMessageNotReadableException ex, HttpServletRequest request) {
            return problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body is missing or malformed.", request);
        }
        @ExceptionHandler(Exception.class)
        ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
            return problem(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "The request could not be completed.", request);
        }
    }
}
