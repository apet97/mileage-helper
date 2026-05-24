package com.cake.clockify.client.auth;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ClockifyTokenVerifierTest {
    private static KeyPair keyPair;
    private static ClockifyTokenVerifier verifier;

    @BeforeAll
    static void setUp() throws Exception {
        KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
        kpg.initialize(2048);
        keyPair = kpg.generateKeyPair();
        verifier = new ClockifyTokenVerifier(keyPair.getPublic());
    }

    @Test
    void verifiesValidTokenSuccessfully() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = createMockJwtWithExtra("clockify", "addon", "my-addon-key", exp, "https://api.test", "https://reports.test", "ws123", keyPair);

        ClockifyClaims claims = verifier.verifyAndParse(token, "my-addon-key");
        assertEquals("clockify", claims.issuer());
        assertEquals("addon", claims.type());
        assertEquals("my-addon-key", claims.subject());
        assertNotNull(claims.expiration());
        assertEquals("https://api.test", claims.backendUrl());
        assertEquals("https://reports.test", claims.reportsUrl());
        assertEquals("ws123", claims.workspaceId());
        assertEquals("user123", claims.userId());

        // Test building client from claims
        com.cake.clockify.client.ClockifyClient client = com.cake.clockify.client.ClockifyClient.fromClaims(claims, "test-token");
        assertEquals("ws123", client.config().workspaceId());
        assertEquals("https://api.test", client.config().backendBaseUrl().toString());
        assertEquals("https://reports.test", client.config().reportsBaseUrl().toString());
    }

    @Test
    void verifyTokenBehavesIdenticallyToVerifyAndParse() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = createMockJwtWithExtra("clockify", "addon", "my-addon-key", exp, "https://api.test", "https://reports.test", "ws123", keyPair);

        ClockifyClaims claims = verifier.verifyToken(token, "my-addon-key");
        assertEquals("clockify", claims.issuer());
        assertEquals("addon", claims.type());
    }

    @Test
    void verifyWebhookReturnsExpectedBooleans() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = createMockJwtWithExtra("clockify", "addon", "my-addon-key", exp, "https://api.test", "https://reports.test", "ws123", keyPair);

        assertTrue(verifier.verifyWebhook(token, "my-addon-key", "ws123"));
        assertFalse(verifier.verifyWebhook(token, "my-addon-key", "wrong-ws"));
        assertFalse(verifier.verifyWebhook("invalid-jwt", "my-addon-key", "ws123"));
    }

    @Test
    void validateWebhookSucceedsOrThrowsAppropriateExceptions() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = createMockJwtWithExtra("clockify", "addon", "my-addon-key", exp, "https://api.test", "https://reports.test", "ws123", keyPair);

        assertDoesNotThrow(() -> verifier.validateWebhook(token, "my-addon-key", "ws123"));

        SecurityException ex1 = assertThrows(SecurityException.class, () -> 
                verifier.validateWebhook(token, "my-addon-key", "wrong-ws"));
        assertTrue(ex1.getMessage().contains("workspace ID mismatch"), ex1.getMessage());

        SecurityException ex2 = assertThrows(SecurityException.class, () -> 
                verifier.validateWebhook(token, "my-addon-key", null));
        assertTrue(ex2.getMessage().contains("workspace ID is missing"), ex2.getMessage());
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        long exp = Instant.now().getEpochSecond() - 10;
        String token = createMockJwt("clockify", "addon", "my-addon-key", exp, keyPair);

        SecurityException ex = assertThrows(SecurityException.class, () -> verifier.verifyAndParse(token, "my-addon-key"));
        assertTrue(ex.getMessage().contains("expired"), ex.getMessage());
    }

    @Test
    void rejectsInvalidIssuer() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = createMockJwt("wrong-iss", "addon", "my-addon-key", exp, keyPair);

        SecurityException ex = assertThrows(SecurityException.class, () -> verifier.verifyAndParse(token, "my-addon-key"));
        assertTrue(ex.getMessage().contains("issuer"), ex.getMessage());
    }

    @Test
    void rejectsInvalidType() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = createMockJwt("clockify", "wrong-type", "my-addon-key", exp, keyPair);

        SecurityException ex = assertThrows(SecurityException.class, () -> verifier.verifyAndParse(token, "my-addon-key"));
        assertTrue(ex.getMessage().contains("type"), ex.getMessage());
    }

    @Test
    void rejectsInvalidSubject() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = createMockJwt("clockify", "addon", "my-addon-key", exp, keyPair);

        SecurityException ex = assertThrows(SecurityException.class, () -> verifier.verifyAndParse(token, "different-key"));
        assertTrue(ex.getMessage().contains("subject"), ex.getMessage());
    }

    @Test
    void rejectsForgedSignature() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        KeyPair otherKeyPair = KeyPairGenerator.getInstance("RSA").generateKeyPair();
        String token = createMockJwt("clockify", "addon", "my-addon-key", exp, otherKeyPair);

        SecurityException ex = assertThrows(SecurityException.class, () -> verifier.verifyAndParse(token, "my-addon-key"));
        assertTrue(ex.getMessage().contains("signature"), ex.getMessage());
    }

    @Test
    void rejectsNonRs256AlgorithmHeaderEvenWhenSignatureBytesVerify() throws Exception {
        long exp = Instant.now().getEpochSecond() + 3600;
        String token = createMockJwtWithAlgorithm("RS512", "clockify", "addon", "my-addon-key", exp, keyPair);

        SecurityException ex = assertThrows(SecurityException.class, () -> verifier.verifyAndParse(token, "my-addon-key"));
        assertTrue(ex.getMessage().contains("algorithm"), ex.getMessage());
    }

    @Test
    void pemConstructorLoadsPublicKeyCorrectly() {
        assertDoesNotThrow(() -> new ClockifyTokenVerifier(ClockifyTokenVerifier.DEFAULT_PUBLIC_KEY_PEM));
    }

    private String createMockJwt(String iss, String type, String sub, long exp, KeyPair kp) throws Exception {
        return createMockJwtWithExtra(iss, type, sub, exp, null, null, null, kp);
    }

    private String createMockJwtWithAlgorithm(String alg, String iss, String type, String sub, long exp, KeyPair kp) throws Exception {
        String header = String.format("{\"alg\":\"%s\",\"typ\":\"JWT\"}", alg);
        String payload = String.format(
                "{\"iss\":\"%s\",\"type\":\"%s\",\"sub\":\"%s\",\"exp\":%d}",
                iss, type, sub, exp);
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));
        String signingInput = encodedHeader + "." + encodedPayload;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(sig.sign());
        return signingInput + "." + encodedSignature;
    }

    private String createMockJwtWithExtra(String iss, String type, String sub, long exp, 
                                          String backendUrl, String reportsUrl, String workspaceId, KeyPair kp) throws Exception {
        String header = "{\"alg\":\"RS256\",\"typ\":\"JWT\"}";
        StringBuilder payloadBuilder = new StringBuilder("{");
        payloadBuilder.append(String.format("\"iss\":\"%s\",\"type\":\"%s\",\"sub\":\"%s\",\"exp\":%d", iss, type, sub, exp));
        if (backendUrl != null) {
            payloadBuilder.append(String.format(",\"backendUrl\":\"%s\"", backendUrl));
        }
        if (reportsUrl != null) {
            payloadBuilder.append(String.format(",\"reportsUrl\":\"%s\"", reportsUrl));
        }
        if (workspaceId != null) {
            payloadBuilder.append(String.format(",\"workspaceId\":\"%s\"", workspaceId));
        }
        payloadBuilder.append(",\"user\":\"user123\"");
        payloadBuilder.append("}");

        String payload = payloadBuilder.toString();
        String encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8));
        String encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8));

        String signingInput = encodedHeader + "." + encodedPayload;

        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initSign(kp.getPrivate());
        sig.update(signingInput.getBytes(StandardCharsets.UTF_8));
        byte[] signature = sig.sign();

        String encodedSignature = Base64.getUrlEncoder().withoutPadding().encodeToString(signature);

        return signingInput + "." + encodedSignature;
    }
}
