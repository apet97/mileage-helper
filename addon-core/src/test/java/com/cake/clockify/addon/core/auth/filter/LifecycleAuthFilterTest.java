package com.cake.clockify.addon.core.auth.filter;

import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class LifecycleAuthFilterTest {

    private TestClockifySignatureParser parser;
    private ClockifyLifecycleAuthFilter filter;

    @BeforeEach
    void setUp() {
        parser = new TestClockifySignatureParser();
        filter = new ClockifyLifecycleAuthFilter(parser);
    }

    static class TestClockifySignatureParser extends ClockifySignatureParser {
        private final java.util.Map<String, Map<String, Object>> mockTokens = new java.util.HashMap<>();

        public TestClockifySignatureParser() {
            super("test-key", generateDummyPublicKey());
        }

        private static java.security.interfaces.RSAPublicKey generateDummyPublicKey() {
            try {
                return (java.security.interfaces.RSAPublicKey) java.security.KeyPairGenerator.getInstance("RSA").generateKeyPair().getPublic();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        public void mockToken(String token, Map<String, Object> claims) {
            mockTokens.put(token, claims);
        }

        @Override
        public Map<String, Object> parseClaims(String token) {
            if (mockTokens.containsKey(token)) {
                return mockTokens.get(token);
            }
            throw new RuntimeException("invalid signature");
        }
    }

    @Test
    void shouldNotFilterNonLifecycleRequests() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/other/path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void returnsUnauthorizedIfTokenHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/lifecycle/installed");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void returnsUnauthorizedIfParserThrows() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/lifecycle/installed");
        request.addHeader("X-Addon-Lifecycle-Token", "invalid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void returnsUnauthorizedIfExpMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/lifecycle/installed");
        request.addHeader("X-Addon-Lifecycle-Token", "token-without-exp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        parser.mockToken("token-without-exp", Map.of(
                "workspaceId", "ws1",
                "addonId", "ad1"
        ));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void returnsUnauthorizedIfWorkspaceIdOrAddonIdMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/lifecycle/installed");
        request.addHeader("X-Addon-Lifecycle-Token", "bad-identity");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        parser.mockToken("bad-identity", Map.of(
                "exp", 9999999999L
        ));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void succeedsAndSetsNormalizedClaimsIfTokenValid() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/lifecycle/installed");
        request.addHeader("X-Addon-Lifecycle-Token", "valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        parser.mockToken("valid-token", Map.of(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "exp", 9999999999L
        ));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        NormalizedClaims claims = RequestAttributes.claims(request);
        assertNotNull(claims);
        assertEquals("ws1", claims.workspaceId());
        assertEquals("ad1", claims.addonId());
    }
}
