package com.cake.clockify.client;

import com.cake.clockify.client.auth.AddonTokenAuthProvider;
import com.cake.clockify.client.auth.ApiKeyAuthProvider;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CoreTypesTest {
    @Test
    void authProvidersDoNotLeakSecretsInToString() {
        assertFalse(new ApiKeyAuthProvider("secret-api-key").toString().contains("secret-api-key"));
        assertFalse(new AddonTokenAuthProvider("secret-addon-token").toString().contains("secret-addon-token"));
    }

    @Test
    void authProvidersApplyExpectedHeaders() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("https://example.test"));
        new ApiKeyAuthProvider("k").apply(builder);
        assertEquals(List.of("k"), builder.build().headers().allValues("X-Api-Key"));
    }

    @Test
    void builderRequiresExplicitBackendBaseUrl() {
        assertThrows(IllegalStateException.class, () -> ClockifyClient.builder().apiKey("secret").buildConfig());
    }

    @Test
    void configBaseFamiliesAreSeparate() {
        String secret = "super-secret-value";
        ClockifyClientConfig config = ClockifyClient.builder()
                .apiKey(secret)
                .backendBaseUrl("https://backend.example.test/api")
                .reportsBaseUrl("https://reports.example.test")
                .buildConfig();
        assertEquals(URI.create("https://backend.example.test/api"), config.baseUrl(ClockifyBaseUrlFamily.BACKEND));
        assertEquals(URI.create("https://reports.example.test"), config.baseUrl(ClockifyBaseUrlFamily.REPORTS));
        assertArrayEquals(new ClockifyBaseUrlFamily[]{ClockifyBaseUrlFamily.BACKEND, ClockifyBaseUrlFamily.REPORTS},
                ClockifyBaseUrlFamily.values());
        assertFalse(config.followRedirects());
        assertFalse(config.toString().contains(secret));
    }

    @Test
    void requestBuildsEncodedQuery() {
        ClockifyRequest request = ClockifyRequest.builder("GET", "v1/workspaces/w/clients")
                .query("page", 1)
                .query("name", "a b")
                .build();
        assertEquals("/v1/workspaces/w/clients?page=1&name=a+b", request.pathWithQuery());
    }

    @Test
    void pageRequestIsBoundedAndOneBased() {
        assertThrows(IllegalArgumentException.class, () -> new ClockifyPageRequest(0, 50));
        assertThrows(IllegalArgumentException.class, () -> new ClockifyPageRequest(1, 5001));
        assertEquals(new ClockifyPageRequest(1, 50), ClockifyPageRequest.firstPage(50));
        assertTrue(ClockifyPage.parseLastPageHeader("true"));
        assertFalse(ClockifyPage.parseLastPageHeader(null));
    }

    @Test
    void boundedReadRejectsOversizeResponses() {
        assertThrows(IOException.class, () -> DefaultClockifyTransport.readBounded(new ByteArrayInputStream("abcdef".getBytes()), 3));
    }

    @Test
    void exceptionMappingUsesSpecificTypesAndSanitizes() {
        ClockifyApiException ex = ClockifyApiException.forStatus(429, Map.of("Retry-After", List.of("2")), "token=secret");
        assertInstanceOf(ClockifyRateLimitException.class, ex);
        assertEquals("2", ((ClockifyRateLimitException) ex).retryAfter());
        assertFalse(ex.sanitizedBody().contains("secret"));
        assertInstanceOf(ClockifyValidationException.class, ClockifyApiException.forStatus(400, Map.of(), "bad"));
        assertInstanceOf(ClockifyAuthException.class, ClockifyApiException.forStatus(403, Map.of(), "no"));
        assertInstanceOf(ClockifyNotFoundException.class, ClockifyApiException.forStatus(404, Map.of(), "missing"));
        assertInstanceOf(ClockifyServerException.class, ClockifyApiException.forStatus(500, Map.of(), "err"));
    }

    @Test
    void exceptionMappingSanitizesJsonAndHeaderStyleSecrets() {
        String body = """
                {
                  "authToken": "install-secret",
                  "x-addon-token": "addon-secret",
                  "X-Api-Key": "api-secret",
                  "password": "password-secret",
                  "message": "Authorization: Bearer bearer-secret"
                }
                X-Addon-Token: header-addon-secret
                """;

        ClockifyApiException ex = ClockifyApiException.forStatus("POST", "/v1/test", 400, Map.of(), body);

        assertFalse(ex.sanitizedBody().contains("install-secret"));
        assertFalse(ex.sanitizedBody().contains("addon-secret"));
        assertFalse(ex.sanitizedBody().contains("api-secret"));
        assertFalse(ex.sanitizedBody().contains("password-secret"));
        assertFalse(ex.sanitizedBody().contains("bearer-secret"));
        assertFalse(ex.sanitizedBody().contains("header-addon-secret"));
    }

    @Test
    void rawClientRejectsUnsafePathsAndRoutesBinarySeparately() {
        ClockifyRawClient raw = new ClockifyRawClient(new FakeTransport());
        ClockifyRequest unsafe = ClockifyRequest.builder("GET", "/admin").build();
        assertThrows(IllegalArgumentException.class, () -> raw.send(unsafe));
        ClockifyRequest binary = ClockifyRequest.builder("GET", "/v1/file/image").binaryResponse(true).build();
        assertThrows(IllegalArgumentException.class, () -> raw.send(binary));
    }

    static final class FakeTransport implements ClockifyTransport {
        @Override public ClockifyResponse<String> send(ClockifyRequest request) { return new ClockifyResponse<>(200, Map.of(), "{}"); }
        @Override public ClockifyBinaryResponse sendBinary(ClockifyRequest request) { return new ClockifyBinaryResponse(200, Map.of(), new byte[0]); }
    }
}
