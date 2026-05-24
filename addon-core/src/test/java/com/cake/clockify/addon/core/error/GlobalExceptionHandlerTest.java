package com.cake.clockify.addon.core.error;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.core.MethodParameter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RestController
class TestController {

    @GetMapping("/test/missing-header")
    public void missingHeader() throws Exception {
        Method method = TestController.class.getMethod("missingHeader");
        MethodParameter parameter = new MethodParameter(method, -1);
        throw new MissingRequestHeaderException("X-Test-Header", parameter);
    }

    @GetMapping("/test/bad-json")
    public void badJson() {
        throw new HttpMessageNotReadableException("Malformed JSON", (org.springframework.http.HttpInputMessage) null);
    }

    @GetMapping("/test/illegal-argument")
    public void illegalArgument() {
        throw new IllegalArgumentException("Invalid argument message");
    }

    @GetMapping("/test/illegal-state")
    public void illegalState() {
        throw new IllegalStateException("Invalid state message");
    }

    @GetMapping("/test/unknown")
    public void unknown() {
        throw new RuntimeException("Unexpected error");
    }
}

@WebMvcTest(controllers = {TestController.class, GlobalExceptionHandler.class})
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void handleMissingHeaderReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/test/missing-header"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("missing_auth_header"))
                .andExpect(jsonPath("$.message").value("Required auth header 'X-Test-Header' is missing"));
    }

    @Test
    void handleBadJsonReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/bad-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_payload"))
                .andExpect(jsonPath("$.message").value("Malformed JSON payload"));
    }

    @Test
    void handleIllegalArgumentReturnsBadRequest() throws Exception {
        mockMvc.perform(get("/test/illegal-argument"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("invalid_request"))
                .andExpect(jsonPath("$.message").value("Invalid request parameters"));
    }

    @Test
    void handleIllegalStateReturnsInternalServerError() throws Exception {
        mockMvc.perform(get("/test/illegal-state"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_error"))
                .andExpect(jsonPath("$.message").value("Internal error"));
    }

    @Test
    void handleExceptionReturnsInternalServerError() throws Exception {
        mockMvc.perform(get("/test/unknown"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("internal_error"))
                .andExpect(jsonPath("$.message").value("Internal error"));
    }
}
