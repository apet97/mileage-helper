package com.cake.clockify.addon.core.auth.filter;

import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class IframeAuthFilterTest {
    private TestClockifySignatureParser parser;
    private ClockifyIframeAuthFilter filter;

    @BeforeEach
    void setUp() {
        parser = new TestClockifySignatureParser();
        filter = new ClockifyIframeAuthFilter(parser);
    }

    @Test
    void iframeRequestUsesAuthTokenQueryParameter() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/iframe/mileage");
        request.setParameter("auth_token", "valid-iframe-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        parser.mockToken("valid-iframe-token", validClaims("ws-iframe"));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        NormalizedClaims claims = RequestAttributes.claims(request);
        assertNotNull(claims);
        assertEquals("ws-iframe", claims.workspaceId());
    }

    @Test
    void apiRequestUsesBearerTokenHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mileage/preview");
        request.addHeader("Authorization", "Bearer valid-api-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        parser.mockToken("valid-api-token", validClaims("ws-api"));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        NormalizedClaims claims = RequestAttributes.claims(request);
        assertNotNull(claims);
        assertEquals("ws-api", claims.workspaceId());
    }

    @Test
    void apiRequestWithoutBearerTokenIsUnauthorized() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/mileage/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void staticAssetsAreIgnored() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/mileage/settings.js");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    private static Map<String, Object> validClaims(String workspaceId) {
        return Map.of(
                "workspaceId", workspaceId,
                "addonId", "mileage-for-clockify",
                "backendUrl", "https://api.clockify.me",
                "userId", "user-1",
                "exp", 9999999999L
        );
    }

    static class TestClockifySignatureParser extends ClockifySignatureParser {
        private final Map<String, Map<String, Object>> mockTokens = new HashMap<>();

        TestClockifySignatureParser() {
            super("mileage-for-clockify", publicKey());
        }

        void mockToken(String token, Map<String, Object> claims) {
            mockTokens.put(token, claims);
        }

        @Override
        public Map<String, Object> parseClaims(String token) {
            if (mockTokens.containsKey(token)) {
                return mockTokens.get(token);
            }
            throw new IllegalArgumentException("invalid signature");
        }

        private static RSAPublicKey publicKey() {
            try {
                return (RSAPublicKey) KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }
    }
}
