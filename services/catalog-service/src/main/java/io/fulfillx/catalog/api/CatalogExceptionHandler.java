package io.fulfillx.catalog.api;

import io.fulfillx.catalog.application.CategoryNotFoundException;
import io.fulfillx.catalog.application.DuplicateSkuException;
import io.fulfillx.catalog.application.ProductNotFoundException;
import io.fulfillx.catalog.domain.CatalogDomainException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class CatalogExceptionHandler {
    @ExceptionHandler({ProductNotFoundException.class, CategoryNotFoundException.class})
    ResponseEntity<ProblemDetail> notFound(RuntimeException ex, HttpServletRequest request) {
        String code = ex instanceof ProductNotFoundException ? "PRODUCT_NOT_FOUND" : "CATEGORY_NOT_FOUND";
        return response(HttpStatus.NOT_FOUND, code, ex.getMessage(), request);
    }

    @ExceptionHandler(DuplicateSkuException.class)
    ResponseEntity<ProblemDetail> conflict(DuplicateSkuException ex, HttpServletRequest request) {
        return response(HttpStatus.CONFLICT, "DUPLICATE_SKU", ex.getMessage(), request);
    }

    @ExceptionHandler(CatalogDomainException.class)
    ResponseEntity<ProblemDetail> domain(CatalogDomainException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, ex.getCode(), ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = ApiErrors.problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", request);
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fields.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", fields);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ProblemDetail> malformed(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", "Request body or parameter could not be parsed.", request);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ProblemDetail> invalidParameter(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return response(HttpStatus.BAD_REQUEST, "INVALID_PARAMETER", "Request parameter or path value has an invalid format.", request);
    }

    private ResponseEntity<ProblemDetail> response(HttpStatus status, String code, String message, HttpServletRequest request) {
        return ResponseEntity.status(status).body(ApiErrors.problem(status, code, message, request));
    }
}
