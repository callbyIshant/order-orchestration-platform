package io.fulfillx.inventory.api;

import io.fulfillx.inventory.domain.InventoryException;
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
public class InventoryExceptionHandler {
    @ExceptionHandler(InventoryException.class)
    ResponseEntity<ProblemDetail> inventory(InventoryException ex, HttpServletRequest request) {
        HttpStatus status = switch (ex.getCode()) {
            case "INVENTORY_NOT_FOUND", "RESERVATION_NOT_FOUND" -> HttpStatus.NOT_FOUND;
            case "STOCK_BELOW_RESERVED", "INVENTORY_ALREADY_EXISTS", "ORDER_PAYLOAD_MISMATCH", "RESERVATION_ID_MISMATCH", "INVALID_RESERVATION_STATE" -> HttpStatus.CONFLICT;
            default -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status).body(InventoryApiErrors.problem(status, ex.getCode(), ex.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ProblemDetail> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        ProblemDetail problem = InventoryApiErrors.problem(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed.", request);
        Map<String, String> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ProblemDetail> malformed(Exception ex, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(InventoryApiErrors.problem(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Request body or path value is invalid.", request));
    }
}
