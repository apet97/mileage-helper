package com.cake.clockify.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import com.sun.net.httpserver.HttpServer;

import static org.junit.jupiter.api.Assertions.*;

class TransportRetryAndConfigTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void builderCarriesWorkspaceCustomHeadersUserAgentAndRetryPolicy() {
        ClockifyClientConfig config = ClockifyClient.builder()
                .apiKey("secret")
                .workspaceId("workspace-1")
                .userAgent("test-agent")
                .header("X-Test", "yes")
                .backendBaseUrl("http://localhost:8081")
                .reportsBaseUrl("http://localhost:8082")
                .retryPolicy(ClockifyRetryPolicy.defaults())
                .buildConfig();
        assertEquals("workspace-1", config.workspaceId());
        assertEquals("test-agent", config.userAgent());
        assertEquals("yes", config.customHeaders().get("X-Test"));
        assertEquals("http://localhost:8081", config.backendBaseUrl().toString());
        assertEquals("http://localhost:8082", config.reportsBaseUrl().toString());
        assertEquals(2, config.retryPolicy().maxRetries());
        assertFalse(config.toString().contains("secret"));
    }

    @Test
    void transportRetriesRetryableStatusesAndSendsHeaders() throws Exception {
        AtomicInteger count = new AtomicInteger();
        try (LocalServer server = new LocalServer(exchange -> {
            count.incrementAndGet();
            assertEquals("test-agent", exchange.getRequestHeaders().getFirst("User-Agent"));
            assertEquals("yes", exchange.getRequestHeaders().getFirst("X-Test"));
            if (count.get() == 1) {
                exchange.getResponseHeaders().add("Retry-After", "0");
                exchange.sendResponseHeaders(429, 0);
            } else {
                byte[] body = "{\"ok\":true}".getBytes();
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            }
            exchange.close();
        })) {
            ClockifyClientConfig config = ClockifyClient.builder()
                    .apiKey("secret")
                    .backendBaseUrl(server.uri())
                    .userAgent("test-agent")
                    .header("X-Test", "yes")
                    .retryPolicy(new ClockifyRetryPolicy(1, Duration.ZERO, Duration.ZERO, java.util.Set.of(429)))
                    .buildConfig();
            ClockifyResponse<String> response = new DefaultClockifyTransport(config).send(ClockifyRequest.builder("GET", "/v1/user").build());
            assertEquals(200, response.statusCode());
            assertEquals(2, count.get());
        }
    }

    @Test
    void transportDoesNotRetryNonRetryableStatusesAndKeepsErrorContext() throws Exception {
        try (LocalServer server = new LocalServer(exchange -> {
            byte[] body = "apiKey=secret".getBytes();
            exchange.getResponseHeaders().add("X-Request-Id", "req-1");
            exchange.sendResponseHeaders(400, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        })) {
            ClockifyClientConfig config = ClockifyClient.builder()
                    .apiKey("secret")
                    .backendBaseUrl(server.uri())
                    .retryPolicy(ClockifyRetryPolicy.defaults())
                    .buildConfig();
            ClockifyValidationException ex = assertThrows(ClockifyValidationException.class,
                    () -> new DefaultClockifyTransport(config).send(ClockifyRequest.builder("POST", "/v1/workspaces/w/clients").jsonBody("{}").build()));
            assertEquals("POST", ex.method());
            assertEquals("/v1/workspaces/w/clients", ex.path());
            assertEquals("req-1", ex.requestId());
            assertFalse(ex.sanitizedBody().contains("secret"));
        }
    }

    @Test
    void reportsAndFilesClientsUseExpectedHostsAndBodyTypes() throws Exception {
        ClientsClientTest.RecordingTransport transport = new ClientsClientTest.RecordingTransport(List.of("{}", "{}"));
        ClockifyClient client = TestClockifyClient.client(transport);
        client.reports().summary("w1", objectMapper.createObjectNode().put("dateRangeStart", "2026-01-01"));
        client.files().uploadImage("a.png", "image/png", new byte[]{1, 2});
        assertEquals(ClockifyBaseUrlFamily.REPORTS, transport.requests.get(0).baseUrlFamily());
        assertEquals("/v1/workspaces/w1/reports/summary", transport.requests.get(0).pathWithQuery());
        assertEquals(ClockifyBaseUrlFamily.BACKEND, transport.requests.get(1).baseUrlFamily());
        assertEquals("/v1/file/image", transport.requests.get(1).pathWithQuery());
        assertNotNull(transport.requests.get(1).bytesBody());
        assertTrue(transport.requests.get(1).contentType().startsWith("multipart/form-data"));
    }

    @Test
    void boundedReadThrowsTypedException() {
        assertThrows(ClockifyResponseTooLargeException.class,
                () -> DefaultClockifyTransport.readBounded(new java.io.ByteArrayInputStream("abcdef".getBytes()), 3));
    }

    @Test
    void transportRejectsHttpsToHttpRedirect() throws Exception {
        FakeHttpClient mockClient = new FakeHttpClient(req ->
            new FakeHttpResponse(302, Map.of("Location", List.of("http://127.0.0.1/somewhere")), new byte[0])
        );

        ClockifyClientConfig config = ClockifyClient.builder()
                .apiKey("secret")
                .backendBaseUrl("https://api.clockify.me/api")
                .followRedirects(true)
                .buildConfig();

        DefaultClockifyTransport transport = new DefaultClockifyTransport(config, mockClient);

        ClockifyTransportException ex = assertThrows(ClockifyTransportException.class, () -> {
            transport.send(ClockifyRequest.builder("GET", "/v1/user").build());
        });
        assertTrue(ex.getMessage().contains("HTTPS to HTTP downgrade redirect rejected"));
    }

    @Test
    void transportRejectsCrossHostRedirectByDefault() throws Exception {
        FakeHttpClient mockClient = new FakeHttpClient(req ->
            new FakeHttpResponse(302, Map.of("Location", List.of("https://other-host.me/api/v1/user")), new byte[0])
        );

        ClockifyClientConfig config = ClockifyClient.builder()
                .apiKey("secret")
                .backendBaseUrl("https://api.clockify.me/api")
                .followRedirects(true)
                .buildConfig();

        DefaultClockifyTransport transport = new DefaultClockifyTransport(config, mockClient);

        ClockifyTransportException ex = assertThrows(ClockifyTransportException.class, () -> {
            transport.send(ClockifyRequest.builder("GET", "/v1/user").build());
        });
        assertTrue(ex.getMessage().contains("Cross-host redirect rejected"));
    }

    @Test
    void transportAllowsCrossHostRedirectIfConfigured() throws Exception {
        java.util.concurrent.atomic.AtomicInteger callCount = new java.util.concurrent.atomic.AtomicInteger();
        FakeHttpClient mockClient = new FakeHttpClient(req -> {
            if (callCount.incrementAndGet() == 1) {
                return new FakeHttpResponse(302, Map.of("Location", List.of("https://other-host.me/api/v1/user")), new byte[0]);
            } else {
                return new FakeHttpResponse(200, Map.of(), "{\"ok\":true}".getBytes());
            }
        });

        ClockifyClientConfig config = ClockifyClient.builder()
                .apiKey("secret")
                .backendBaseUrl("https://api.clockify.me/api")
                .followRedirects(true)
                .allowCrossHostRedirects(true)
                .buildConfig();

        DefaultClockifyTransport transport = new DefaultClockifyTransport(config, mockClient);
        ClockifyResponse<String> response = transport.send(ClockifyRequest.builder("GET", "/v1/user").build());
        assertEquals(200, response.statusCode());
        assertEquals("{\"ok\":true}", response.body());
    }

    @Test
    void transportRejectsTooManyRedirects() throws Exception {
        FakeHttpClient mockClient = new FakeHttpClient(req ->
            new FakeHttpResponse(302, Map.of("Location", List.of("https://api.clockify.me/api/v1/user")), new byte[0])
        );

        ClockifyClientConfig config = ClockifyClient.builder()
                .apiKey("secret")
                .backendBaseUrl("https://api.clockify.me/api")
                .followRedirects(true)
                .buildConfig();

        DefaultClockifyTransport transport = new DefaultClockifyTransport(config, mockClient);

        ClockifyTransportException ex = assertThrows(ClockifyTransportException.class, () -> {
            transport.send(ClockifyRequest.builder("GET", "/v1/user").build());
        });
        assertTrue(ex.getMessage().contains("Too many redirects"));
    }

    @Test
    void transportPreservesPostBodyAndContentTypeAcrossTemporaryRedirect() throws Exception {
        assertRedirectPreservesPostBodyAndContentType(307);
    }

    @Test
    void transportPreservesPostBodyAndContentTypeAcrossPermanentRedirect() throws Exception {
        assertRedirectPreservesPostBodyAndContentType(308);
    }

    private void assertRedirectPreservesPostBodyAndContentType(int redirectStatus) throws Exception {
        AtomicInteger count = new AtomicInteger();
        String json = "{\"name\":\"Acme\"}";
        try (LocalServer server = new LocalServer(exchange -> {
            int call = count.incrementAndGet();
            if (call == 1) {
                assertEquals("POST", exchange.getRequestMethod());
                assertEquals("/v1/workspaces/w/clients", exchange.getRequestURI().getPath());
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().add("Location", "/redirected");
                exchange.sendResponseHeaders(redirectStatus, -1);
                exchange.close();
                return;
            }

            assertEquals("POST", exchange.getRequestMethod());
            assertEquals("/redirected", exchange.getRequestURI().getPath());
            assertEquals("application/json", exchange.getRequestHeaders().getFirst("Content-Type"));
            assertEquals(json, new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"ok\":true}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        })) {
            ClockifyClientConfig config = ClockifyClient.builder()
                    .apiKey("secret")
                    .backendBaseUrl(server.uri())
                    .followRedirects(true)
                    .buildConfig();

            ClockifyResponse<String> response = new DefaultClockifyTransport(config)
                    .send(ClockifyRequest.builder("POST", "/v1/workspaces/w/clients").jsonBody(json).build());

            assertEquals(200, response.statusCode());
            assertEquals("{\"ok\":true}", response.body());
            assertEquals(2, count.get());
        }
    }

    interface Handler { void handle(com.sun.net.httpserver.HttpExchange exchange) throws IOException; }

    static final class LocalServer implements AutoCloseable {
        private final HttpServer server;
        LocalServer(Handler handler) throws IOException {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> handler.handle(exchange));
            server.start();
        }
        java.net.URI uri() { return java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()); }
        @Override public void close() { server.stop(0); }
    }

    static class FakeHttpClient extends java.net.http.HttpClient {
        private final java.util.function.Function<java.net.http.HttpRequest, java.net.http.HttpResponse<java.io.InputStream>> sendHandler;

        FakeHttpClient(java.util.function.Function<java.net.http.HttpRequest, java.net.http.HttpResponse<java.io.InputStream>> sendHandler) {
            this.sendHandler = sendHandler;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> java.net.http.HttpResponse<T> send(java.net.http.HttpRequest request, java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) throws IOException, InterruptedException {
            return (java.net.http.HttpResponse<T>) sendHandler.apply(request);
        }

        @Override public java.util.Optional<java.net.CookieHandler> cookieHandler() { return java.util.Optional.empty(); }
        @Override public java.util.Optional<java.time.Duration> connectTimeout() { return java.util.Optional.empty(); }
        @Override public java.net.http.HttpClient.Redirect followRedirects() { return java.net.http.HttpClient.Redirect.NEVER; }
        @Override public java.util.Optional<java.net.ProxySelector> proxy() { return java.util.Optional.empty(); }
        @Override public javax.net.ssl.SSLContext sslContext() { return null; }
        @Override public javax.net.ssl.SSLParameters sslParameters() { return null; }
        @Override public java.util.Optional<java.net.Authenticator> authenticator() { return java.util.Optional.empty(); }
        @Override public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_2; }
        @Override public java.util.Optional<java.util.concurrent.Executor> executor() { return java.util.Optional.empty(); }
        @Override public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(java.net.http.HttpRequest request, java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler) { return null; }
        @Override public <T> java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<T>> sendAsync(java.net.http.HttpRequest request, java.net.http.HttpResponse.BodyHandler<T> responseBodyHandler, java.net.http.HttpResponse.PushPromiseHandler<T> pushPromiseHandler) { return null; }
    }

    static class FakeHttpResponse implements java.net.http.HttpResponse<java.io.InputStream> {
        private final int statusCode;
        private final java.net.http.HttpHeaders headers;
        private final java.io.InputStream body;

        FakeHttpResponse(int statusCode, Map<String, List<String>> headersMap, byte[] bodyBytes) {
            this.statusCode = statusCode;
            this.headers = java.net.http.HttpHeaders.of(headersMap, (k, v) -> true);
            this.body = new java.io.ByteArrayInputStream(bodyBytes);
        }

        @Override public int statusCode() { return statusCode; }
        @Override public java.net.http.HttpRequest request() { return null; }
        @Override public java.util.Optional<java.net.http.HttpResponse<java.io.InputStream>> previousResponse() { return java.util.Optional.empty(); }
        @Override public java.net.http.HttpHeaders headers() { return headers; }
        @Override public java.io.InputStream body() { return body; }
        @Override public java.net.URI uri() { return null; }
        @Override public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return java.util.Optional.empty(); }
        @Override public java.net.http.HttpClient.Version version() { return java.net.http.HttpClient.Version.HTTP_2; }
    }
}
