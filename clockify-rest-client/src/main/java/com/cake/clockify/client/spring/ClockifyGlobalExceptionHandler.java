package com.cake.clockify.client.spring;

import com.cake.clockify.client.ClockifyApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Global exception handler for {@link ClockifyApiException}.
 * Ensures that any error returned by Clockify is sanitized and forwarded with the correct status code.
 */
@RestControllerAdvice
public class ClockifyGlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ClockifyGlobalExceptionHandler.class);
    private final ObjectMapper objectMapper = new ObjectMapper();

    @ExceptionHandler(ClockifyApiException.class)
    public ResponseEntity<JsonNode> handleClockifyApiException(ClockifyApiException e) {
        log.debug("Clockify API error: status={}, message={}", e.statusCode(), e.getMessage());
        try {
            if (e.sanitizedBody() != null && !e.sanitizedBody().isBlank()) {
                JsonNode errorNode = objectMapper.readTree(e.sanitizedBody());
                return ResponseEntity.status(e.statusCode())
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(errorNode);
            }
        } catch (Exception ignored) {
            // Fall back to structured fallback if parsing fails
        }
        return ResponseEntity.status(e.statusCode())
                .contentType(MediaType.APPLICATION_JSON)
                .body(objectMapper.createObjectNode()
                        .put("error", "clockify_api_error")
                        .put("message", e.getMessage())
                        .put("details", e.sanitizedBody()));
    }
}
