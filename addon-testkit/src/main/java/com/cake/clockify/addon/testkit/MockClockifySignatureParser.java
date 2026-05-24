package com.cake.clockify.addon.testkit;

import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A mock signature parser that can be used in integration tests to bypass real JWT verification.
 */
public class MockClockifySignatureParser extends ClockifySignatureParser {
    private static final Map<String, Object> claims = new ConcurrentHashMap<>();
    private final String addonKey;

    public MockClockifySignatureParser(String addonKey, RSAPublicKey publicKey) {
        super(addonKey, publicKey);
        this.addonKey = addonKey;
    }

    public static void setClaims(Map<String, Object> newClaims) {
        claims.clear();
        if (newClaims != null) {
            claims.putAll(newClaims);
        }
    }

    @Override
    public Map<String, Object> parseClaims(String token) {
        if (!claims.isEmpty()) {
            Map<String, Object> finalClaims = new java.util.HashMap<>(claims);
            finalClaims.putIfAbsent("iss", "clockify");
            finalClaims.putIfAbsent("type", "addon");
            finalClaims.putIfAbsent("sub", addonKey);
            return finalClaims;
        }
        return super.parseClaims(token);
    }
}
