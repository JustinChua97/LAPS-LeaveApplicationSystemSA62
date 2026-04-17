package com.iss.laps.controller.rest;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.exception.ResourceNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.Map;

/**
 * JSON error handler for all REST controllers under /api/v1
 * (basePackages covers LeaveRestController, JwtAuthController, and any future additions).
 * Ordered highest precedence so it runs before GlobalExceptionHandler,
 * which returns Thymeleaf views and would otherwise intercept API exceptions.
 * ASVS V7.4.1: no stack traces or internals in responses.
 * ASVS V8.2.1: no entity internals or SQL details exposed.
 */
@RestControllerAdvice(basePackages = "com.iss.laps.controller.rest")
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ApiExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, String>> handleNotFound(ResourceNotFoundException ex) {
        log.warn("API resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("error", "Not Found", "message", "Resource not found"));
    }

    @ExceptionHandler({LeaveApplicationException.class, IllegalArgumentException.class,
                       MethodArgumentTypeMismatchException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception ex) {
        log.warn("API bad request: {}", ex.getMessage());
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Bad Request", "message", "Invalid request"));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGeneric(Exception ex) {
        log.error("Unexpected API error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Internal Server Error", "message", "An unexpected error occurred"));
    }
}
