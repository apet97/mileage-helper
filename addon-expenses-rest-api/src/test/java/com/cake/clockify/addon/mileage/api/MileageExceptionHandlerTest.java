package com.cake.clockify.addon.mileage.api;

import com.cake.clockify.addon.mileage.api.model.MileageErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

class MileageExceptionHandlerTest {
    private final MileageExceptionHandler handler = new MileageExceptionHandler(new ObjectMapper());

    @Test
    void maxUploadSizeExceededMapsToFourHundredWithFriendlyMessage() {
        MaxUploadSizeExceededException exception = new MaxUploadSizeExceededException(10L * 1024L * 1024L);

        ResponseEntity<MileageErrorResponse> response = handler.handleMaxUploadSize(exception);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        MileageErrorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.error()).isEqualTo("receipt_too_large");
        assertThat(body.message()).isEqualTo("Receipt file exceeds 10 MB");
        assertThat(body.fields()).isNull();
        assertThat(body.upstreamMessage()).isNull();
        assertThat(body.retryAfter()).isNull();
    }
}
