package com.cake.clockify.client.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;

/**
 * Utility to verify and parse JWT tokens issued by Clockify.
 * Checks RSA256 signature, expiry, issuer (iss=clockify), and type (type=addon).
 */
public final class ClockifyTokenVerifier {
    public static final String DEFAULT_PUBLIC_KEY_PEM = 
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAubktufFNO/op+E5WBWL6\n" +
        "/Y9QRZGSGGCsV00FmPRl5A0mSfQu3yq2Yaq47IlN0zgFy9IUG8/JJfwiehsmbrKa\n" +
        "49t/xSkpG1u9w1GUyY0g4eKDUwofHKAt3IPw0St4qsWLK9mO+koUo56CGQOEpTui\n" +
        "5bMfmefVBBfShXTaZOtXPB349FdzSuYlU/5o3L12zVWMutNhiJCKyGfsuu2uXa9+\n" +
        "6uQnZBw1wO3/QEci7i4TbC+ZXqW1rCcbogSMORqHAP6qSAcTFRmrjFAEsOWiUUhZ\n" +
        "rLDg2QJ8VTDghFnUhYklNTJlGgfo80qEWe1NLIwvZj0h3bWRfrqZHsD/Yjh0duk6\n" +
        "yQIDAQAB";

    private final PublicKey publicKey;
    private final ObjectMapper objectMapper;

    public ClockifyTokenVerifier() {
        this(DEFAULT_PUBLIC_KEY_PEM);
    }

    public ClockifyTokenVerifier(PublicKey publicKey) {
        this.publicKey = Objects.requireNonNull(publicKey, "publicKey");
        this.objectMapper = new ObjectMapper();
    }

    public ClockifyTokenVerifier(String pemPublicKey) {
        Objects.requireNonNull(pemPublicKey, "pemPublicKey");
        try {
            String cleanPem = pemPublicKey
                    .replace("-----BEGIN PUBLIC KEY-----", "")
                    .replace("-----END PUBLIC KEY-----", "")
                    .replaceAll("\\s+", "");
            byte[] keyBytes = Base64.getDecoder().decode(cleanPem);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            this.publicKey = kf.generatePublic(spec);
            this.objectMapper = new ObjectMapper();
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to load public key from PEM", e);
        }
    }

    /**
     * Verifies the signature and standard claims of a Clockify JWT token.
     *
     * @param token             the JWT token string
     * @param expectedAddonKey  optional expected value of the 'sub' claim (add-on key)
     * @return the ClockifyClaims object if verification succeeds
     * @throws SecurityException if verification fails (signature, expired, or invalid claims)
     */
    public ClockifyClaims verifyAndParse(String token, String expectedAddonKey) {
        Objects.requireNonNull(token, "token");
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Invalid JWT token format");
        }

        // 1. Enforce the official RSA256 JWT algorithm before validating bytes.
        Map<String, Object> rawHeader;
        try {
            String headerJson = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8);
            rawHeader = objectMapper.readValue(headerJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse token header", e);
        }

        if (!"RS256".equals(rawHeader.get("alg"))) {
            throw new SecurityException("Invalid token algorithm: expected RS256");
        }

        // 2. Verify RSA256 signature
        try {
            Signature sig = Signature.getInstance("SHA256withRSA");
            sig.initVerify(publicKey);
            sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8));
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[2]);
            if (!sig.verify(signatureBytes)) {
                throw new SecurityException("Token signature verification failed");
            }
        } catch (Exception e) {
            throw new SecurityException("Token signature verification failed", e);
        }

        // 3. Decode claims payload
        Map<String, Object> rawClaims;
        try {
            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
            rawClaims = objectMapper.readValue(payloadJson, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalArgumentException("Failed to parse token payload", e);
        }

        // 4. Verify standard claims
        if (!"clockify".equals(rawClaims.get(ClockifyClaims.CLAIM_ISSUER))) {
            throw new SecurityException("Invalid token issuer: expected 'clockify'");
        }

        if (!"addon".equals(rawClaims.get(ClockifyClaims.CLAIM_TYPE))) {
            throw new SecurityException("Invalid token type: expected 'addon'");
        }

        if (expectedAddonKey != null && !expectedAddonKey.isBlank()) {
            if (!expectedAddonKey.equals(rawClaims.get(ClockifyClaims.CLAIM_SUBJECT))) {
                throw new SecurityException("Invalid token subject: expected '" + expectedAddonKey + "'");
            }
        }

        // Verify expiration
        Instant expiration = null;
        Object expObj = rawClaims.get(ClockifyClaims.CLAIM_EXPIRATION);
        if (expObj instanceof Number expNumber) {
            long expSeconds = expNumber.longValue();
            long currentSeconds = Instant.now().getEpochSecond();
            if (currentSeconds > expSeconds) {
                throw new SecurityException("Token has expired");
            }
            expiration = Instant.ofEpochSecond(expSeconds);
        }

        return new ClockifyClaims(
                (String) rawClaims.get(ClockifyClaims.CLAIM_ISSUER),
                (String) rawClaims.get(ClockifyClaims.CLAIM_SUBJECT),
                (String) rawClaims.get(ClockifyClaims.CLAIM_TYPE),
                expiration,
                (String) rawClaims.get(ClockifyClaims.CLAIM_BACKEND_URL),
                (String) rawClaims.get(ClockifyClaims.CLAIM_REPORTS_URL),
                (String) rawClaims.get(ClockifyClaims.CLAIM_PTO_URL),
                (String) rawClaims.get(ClockifyClaims.CLAIM_LOCATIONS_URL),
                (String) rawClaims.get(ClockifyClaims.CLAIM_SCREENSHOTS_URL),
                (String) rawClaims.get(ClockifyClaims.CLAIM_WORKSPACE_ID),
                (String) rawClaims.get(ClockifyClaims.CLAIM_ADDON_ID),
                (String) rawClaims.get(ClockifyClaims.CLAIM_USER_ID),
                (String) rawClaims.get(ClockifyClaims.CLAIM_WORKSPACE_ROLE),
                (String) rawClaims.get(ClockifyClaims.CLAIM_LANGUAGE),
                (String) rawClaims.get(ClockifyClaims.CLAIM_THEME),
                rawClaims
        );
    }

    /**
     * Alias for {@link #verifyAndParse(String, String)} to match documentation patterns.
     *
     * @param token             the JWT token string
     * @param expectedAddonKey  optional expected value of the 'sub' claim (add-on key)
     * @return the ClockifyClaims object if verification succeeds
     * @throws SecurityException if verification fails
     */
    public ClockifyClaims verifyToken(String token, String expectedAddonKey) {
        return verifyAndParse(token, expectedAddonKey);
    }

    /**
     * Helper to validate that a webhook signature is valid and matches the target workspace.
     * Throws descriptive exceptions for debugging and auditing purposes.
     *
     * @param signatureHeader    the signature header (JWT token) from the request
     * @param expectedAddonKey   the expected addon key
     * @param payloadWorkspaceId the workspace ID extracted from the webhook event body
     * @throws SecurityException if verification fails or workspace ID mismatches
     */
    public void validateWebhook(String signatureHeader, String expectedAddonKey, String payloadWorkspaceId) {
        ClockifyClaims claims = verifyAndParse(signatureHeader, expectedAddonKey);
        if (payloadWorkspaceId == null) {
            throw new SecurityException("Webhook payload workspace ID is missing");
        }
        if (!payloadWorkspaceId.equals(claims.workspaceId())) {
            throw new SecurityException("Webhook workspace ID mismatch: payload=" + payloadWorkspaceId 
                    + ", token=" + claims.workspaceId());
        }
    }

    /**
     * Helper to verify that a webhook signature is valid and matches the target workspace.
     * Returns false on any failure instead of throwing exceptions.
     */
    public boolean verifyWebhook(String signatureHeader, String expectedAddonKey, String payloadWorkspaceId) {
        try {
            validateWebhook(signatureHeader, expectedAddonKey, payloadWorkspaceId);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
