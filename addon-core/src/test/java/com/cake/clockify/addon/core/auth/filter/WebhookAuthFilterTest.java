package com.cake.clockify.addon.core.auth.filter;

import com.cake.clockify.addonsdk.clockify.ClockifySignatureParser;
import com.cake.clockify.addon.core.auth.NormalizedClaims;
import com.cake.clockify.addon.core.auth.RequestAttributes;
import com.cake.clockify.addon.core.config.ClockifyAddonProperties;
import com.cake.clockify.addon.core.crypto.TokenCodec;
import com.cake.clockify.addon.core.webhook.WebhookTokenLookup;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class WebhookAuthFilterTest {

    private TestClockifySignatureParser parser;
    private WebhookTokenLookup tokenLookup;
    private TokenCodec codec;
    private ClockifyWebhookAuthFilter filter;

    @BeforeEach
    void setUp() {
        parser = new TestClockifySignatureParser();
        tokenLookup = Mockito.mock(WebhookTokenLookup.class);
        codec = new TokenCodec(Map.of("k1", "00000000000000000000000000000000000000000000000000000000000000aa"), "k1");
        ClockifyAddonProperties props = new ClockifyAddonProperties(
                "test-key", "Test Addon", "https://example.com", "Test",
                true, "", "", null);
        filter = new ClockifyWebhookAuthFilter(parser, tokenLookup, codec, props);
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
    void shouldNotFilterNonWebhookRequests() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/other/path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
    }

    @Test
    void returnsUnauthorizedIfSignatureHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/event-path");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void returnsUnauthorizedIfParserThrows() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/event-path");
        request.addHeader("Clockify-Signature", "bad-sig");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void returnsForbiddenIfNoStoredTokenFound() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/event-path");
        request.addHeader("Clockify-Signature", "valid-sig");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        parser.mockToken("valid-sig", claims(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "exp", 9999999999L
        ));
        when(tokenLookup.findByWorkspaceAddonPath("ws1", "ad1", "/event-path")).thenReturn(Optional.empty());

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void returnsForbiddenIfStoredWebhookTokenDoesNotMatchSignatureHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/event-path");
        request.addHeader("Clockify-Signature", "valid-sig");
        request.addHeader("Clockify-Webhook-Event-Type", "TIME_ENTRY_CREATED");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        parser.mockToken("valid-sig", claims(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "exp", 9999999999L
        ));

        TokenCodec.Encrypted encrypted = codec.encrypt("actual-token");
        String b64Encrypted = Base64.getEncoder().encodeToString(encrypted.cipher());

        WebhookTokenLookup.StoredWebhookToken storedToken = new WebhookTokenLookup.StoredWebhookToken(
                "ws1", "ad1", "/event-path", "TIME_ENTRY_CREATED", b64Encrypted, "k1"
        );

        when(tokenLookup.findByWorkspaceAddonPath("ws1", "ad1", "/event-path")).thenReturn(Optional.of(storedToken));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_FORBIDDEN, response.getStatus());
    }

    @Test
    void returnsUnauthorizedIfEventTypeHeaderMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/event-path");
        request.addHeader("Clockify-Signature", "valid-sig");
        // No event type header
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        parser.mockToken("valid-sig", claims(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "exp", 9999999999L,
                "authToken", "actual-token"
        ));

        TokenCodec.Encrypted encrypted = codec.encrypt("valid-sig");
        String b64Encrypted = Base64.getEncoder().encodeToString(encrypted.cipher());

        WebhookTokenLookup.StoredWebhookToken storedToken = new WebhookTokenLookup.StoredWebhookToken(
                "ws1", "ad1", "/event-path", "TIME_ENTRY_CREATED", b64Encrypted, "k1"
        );

        when(tokenLookup.findByWorkspaceAddonPath("ws1", "ad1", "/event-path")).thenReturn(Optional.of(storedToken));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
    }

    @Test
    void succeedsAndSetsAttributesIfAllChecksPass() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/event-path");
        request.addHeader("Clockify-Signature", "valid-sig");
        request.addHeader("Clockify-Webhook-Event-Type", "TIME_ENTRY_CREATED");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        parser.mockToken("valid-sig", claims(
                "workspaceId", "ws1",
                "addonId", "ad1",
                "exp", 9999999999L,
                "authToken", "actual-token"
        ));

        TokenCodec.Encrypted encrypted = codec.encrypt("valid-sig");
        String b64Encrypted = Base64.getEncoder().encodeToString(encrypted.cipher());

        WebhookTokenLookup.StoredWebhookToken storedToken = new WebhookTokenLookup.StoredWebhookToken(
                "ws1", "ad1", "/event-path", "TIME_ENTRY_CREATED", b64Encrypted, "k1"
        );

        when(tokenLookup.findByWorkspaceAddonPath("ws1", "ad1", "/event-path")).thenReturn(Optional.of(storedToken));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        NormalizedClaims claims = RequestAttributes.claims(request);
        assertNotNull(claims);
        assertEquals("ws1", claims.workspaceId());
        assertEquals("ad1", claims.addonId());
        assertEquals("TIME_ENTRY_CREATED", request.getAttribute(ClockifyWebhookAuthFilter.ATTR_EVENT_TYPE));
    }

    @Test
    void succeedsWithDirectSignatureTokenMatchAndNoExp() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/webhook/event-path");
        request.addHeader("Clockify-Signature", "actual-token-sig");
        request.addHeader("Clockify-Webhook-Event-Type", "TIME_ENTRY_CREATED");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        parser.mockToken("actual-token-sig", claims(
                "workspaceId", "ws1",
                "addonId", "ad1"
                // No exp claim, no authToken claim
        ));

        TokenCodec.Encrypted encrypted = codec.encrypt("actual-token-sig");
        String b64Encrypted = Base64.getEncoder().encodeToString(encrypted.cipher());

        WebhookTokenLookup.StoredWebhookToken storedToken = new WebhookTokenLookup.StoredWebhookToken(
                "ws1", "ad1", "/event-path", "TIME_ENTRY_CREATED", b64Encrypted, "k1"
        );

        when(tokenLookup.findByWorkspaceAddonPath("ws1", "ad1", "/event-path")).thenReturn(Optional.of(storedToken));

        filter.doFilter(request, response, chain);

        assertEquals(HttpServletResponse.SC_OK, response.getStatus());
        NormalizedClaims claims = RequestAttributes.claims(request);
        assertNotNull(claims);
        assertEquals("ws1", claims.workspaceId());
        assertEquals("ad1", claims.addonId());
    }

    private static Map<String, Object> claims(Object... entries) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("iss", "clockify");
        claims.put("type", "addon");
        claims.put("sub", "test-key");
        for (int i = 0; i < entries.length; i += 2) {
            claims.put((String) entries[i], entries[i + 1]);
        }
        return claims;
    }
}
