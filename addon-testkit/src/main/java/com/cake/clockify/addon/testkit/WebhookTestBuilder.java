package com.cake.clockify.addon.testkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.HashMap;
import java.util.Map;

/**
 * Fluent builder for Clockify Add-on Webhook requests (MockMvc).
 */
public class WebhookTestBuilder {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private String eventType = "UNKNOWN";
    private String path = "/webhook";
    private String signature = "dummy-webhook-signature";
    private boolean signatureExplicit;
    private String webhookToken = "dummy-token";
    private Object payload;
    private final Map<String, Object> claims = new HashMap<>();

    public static WebhookTestBuilder event(String eventType) {
        WebhookTestBuilder b = new WebhookTestBuilder();
        b.eventType = eventType;
        return b;
    }

    public WebhookTestBuilder path(String path) {
        this.path = path;
        return this;
    }

    public WebhookTestBuilder workspaceId(String workspaceId) {
        this.claims.put("workspaceId", workspaceId);
        return this;
    }

    public WebhookTestBuilder addonId(String addonId) {
        this.claims.put("addonId", addonId);
        return this;
    }

    public WebhookTestBuilder webhookToken(String token) {
        this.webhookToken = token;
        return this;
    }

    public WebhookTestBuilder signature(String signature) {
        this.signature = signature;
        this.signatureExplicit = true;
        return this;
    }

    public WebhookTestBuilder payload(Object payload) {
        this.payload = payload;
        return this;
    }

    public MockHttpServletRequestBuilder build() throws Exception {
        String body = payload instanceof String s ? s : objectMapper.writeValueAsString(payload);
        String signatureHeader = signatureExplicit ? signature : webhookToken;

        // Auto-configure mock claims for signature verification
        Map<String, Object> finalClaims = new HashMap<>(claims);
        finalClaims.put("iat", System.currentTimeMillis() / 1000);
        finalClaims.put("exp", (System.currentTimeMillis() / 1000) + 3600);
        MockClockifySignatureParser.setClaims(finalClaims);

        return MockMvcRequestBuilders.post(path)
                .header("Clockify-Signature", signatureHeader)
                .header("Clockify-Webhook-Event-Type", eventType)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }
}
