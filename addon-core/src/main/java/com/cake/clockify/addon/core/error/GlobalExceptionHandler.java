package com.cake.clockify.addon.core.error;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Centralized sanitized-envelope error mapping. Controllers never wrap calls in try/catch — these
 * exceptions bubble up and land here. Responses never include stack traces or Spring's default
 * error schema. Add-on-specific exceptions should be mapped by extending this class or adding
 * their own {@code @RestControllerAdvice}.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<Map<String, Object>> handleMissingHeader(MissingRequestHeaderException e) {
        return respond(HttpStatus.UNAUTHORIZED, "missing_auth_header",
                "Required auth header '" + e.getHeaderName() + "' is missing");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleBadJson(HttpMessageNotReadableException e) {
        return respond(HttpStatus.BAD_REQUEST, "invalid_payload", "Malformed JSON payload");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.debug("IllegalArgumentException: {}", e.getMessage(), e);
        return respond(HttpStatus.BAD_REQUEST, "invalid_request", "Invalid request parameters");
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException e) {
        log.error("IllegalStateException: {}", e.getMessage(), e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal error");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnknown(Exception e) {
        log.error("Unhandled exception", e);
        return respond(HttpStatus.INTERNAL_SERVER_ERROR, "internal_error", "Internal error");
    }

    protected ResponseEntity<Map<String, Object>> respond(HttpStatus status, String code, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        if (message != null && !message.isBlank()) {
            body.put("message", message);
        }
        return ResponseEntity.status(status).body(body);
    }
}
