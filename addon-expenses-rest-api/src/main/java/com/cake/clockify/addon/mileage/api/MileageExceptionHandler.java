package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.mileage.api.model.MileageErrorResponse;
import com.cake.clockify.client.ClockifyApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MileageExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(MileageExceptionHandler.class);

    private final ObjectMapper objectMapper;

    public MileageExceptionHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @ExceptionHandler(ClockifyApiException.class)
    public ResponseEntity<MileageErrorResponse> handleClockifyApiError(ClockifyApiException e) {
        HttpStatusCode status = safeStatus(e.statusCode());
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(status);
        if (e.retryAfter() != null) {
            builder.header("Retry-After", e.retryAfter());
        }
        return builder.body(new MileageErrorResponse(
                "clockify_api_error",
                clientSafeMessage(e.statusCode()),
                null,
                extractMessage(e.sanitizedBody()),
                e.retryAfter()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<MileageErrorResponse> handleValidationErrors(MethodArgumentNotValidException e) {
        Map<String, String> fields = e.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        error -> error.getDefaultMessage() == null ? "Invalid value" : error.getDefaultMessage(),
                        (left, right) -> left,
                        LinkedHashMap::new));
        return ResponseEntity.badRequest().body(new MileageErrorResponse(
                "validation_failed",
                "Input validation failed",
                fields,
                null,
                null));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<MileageErrorResponse> handleMalformedJson(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new MileageErrorResponse(
                "malformed_request",
                "Request body could not be parsed as JSON",
                null,
                null,
                null));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<MileageErrorResponse> handleIllegalArgument(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(new MileageErrorResponse(
                "invalid_request",
                safeMessage(e.getMessage(), "Request is invalid"),
                null,
                null,
                null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<MileageErrorResponse> handleResponseStatus(ResponseStatusException e) {
        String message = e.getReason() == null ? clientSafeMessage(e.getStatusCode().value()) : e.getReason();
        String error = e.getStatusCode().is4xxClientError() ? "bad_request" : "server_error";
        if (message.toLowerCase(java.util.Locale.ROOT).contains("config")
                || message.toLowerCase(java.util.Locale.ROOT).contains("category")
                || message.toLowerCase(java.util.Locale.ROOT).contains("settings")) {
            error = "configuration_missing";
        }
        return ResponseEntity.status(e.getStatusCode()).body(new MileageErrorResponse(
                error,
                message,
                null,
                null,
                null));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<MileageErrorResponse> handleNoResource(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new MileageErrorResponse(
                "not_found",
                "Resource not found",
                null,
                null,
                null));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<MileageErrorResponse> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(new MileageErrorResponse(
                "receipt_too_large",
                "Receipt file exceeds 10 MB",
                null,
                null,
                null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<MileageErrorResponse> handleUnexpected(Exception e) {
        log.error("Unexpected mileage request failure", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new MileageErrorResponse(
                "internal_error",
                "An unexpected error occurred while processing the mileage request",
                null,
                null,
                null));
    }

    private String extractMessage(String sanitizedBody) {
        if (sanitizedBody == null || sanitizedBody.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(sanitizedBody);
            JsonNode message = node.path("message");
            if (message.isTextual() && !message.asText().isBlank()) {
                return truncate(message.asText(), 500);
            }
        } catch (Exception ignored) {
            return null;
        }
        return null;
    }

    private static String clientSafeMessage(int status) {
        return switch (status) {
            case 400, 422 -> "The Clockify API rejected the mileage expense as invalid";
            case 401, 403 -> "The Clockify API rejected the request due to authentication or authorization";
            case 404 -> "The requested Clockify resource was not found";
            case 409 -> "The request conflicts with the current Clockify resource state";
            case 429 -> "The Clockify API is rate-limiting; please retry later";
            default -> status >= 500
                    ? "The Clockify API is temporarily unavailable; please retry later"
                    : "The Clockify API returned an error";
        };
    }

    private static HttpStatusCode safeStatus(int code) {
        if (code < 100 || code > 599) {
            return HttpStatus.BAD_GATEWAY;
        }
        try {
            return HttpStatusCode.valueOf(code);
        } catch (IllegalArgumentException e) {
            return HttpStatus.BAD_GATEWAY;
        }
    }

    private static String safeMessage(String message, String fallback) {
        return message == null || message.isBlank() ? fallback : truncate(message, 500);
    }

    private static String truncate(String value, int max) {
        return value.length() <= max ? value : value.substring(0, max);
    }
}
