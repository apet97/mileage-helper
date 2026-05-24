package com.cake.clockify.client.spring;

import com.cake.clockify.client.ClockifyClientConfig;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyResponse;
import com.cake.clockify.client.ClockifyBinaryResponse;
import com.cake.clockify.client.auth.ApiKeyAuthProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

import java.net.URI;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebClientClockifyTransportTest {

    private MockWebServer mockWebServer;
    private WebClientClockifyTransport transport;

    @BeforeEach
    void setUp() throws Exception {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        ClockifyClientConfig config = com.cake.clockify.client.ClockifyClient.builder()
                .backendBaseUrl(mockWebServer.url("/").uri())
                .reportsBaseUrl(mockWebServer.url("/").uri())
                .apiKey("test-key")
                .workspaceId("workspace-1")
                .userAgent("test-agent")
                .header("X-Custom", "custom-value")
                .retryPolicy(com.cake.clockify.client.ClockifyRetryPolicy.none())
                .requestTimeout(java.time.Duration.ofSeconds(2))
                .maxResponseBytes(1024 * 1024)
                .followRedirects(false)
                .allowCrossHostRedirects(false)
                .buildConfig();

        transport = new WebClientClockifyTransport(config, WebClient.builder());
    }

    @AfterEach
    void tearDown() throws Exception {
        mockWebServer.shutdown();
    }

    @Test
    void testSendStringResponse() throws Exception {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"success\":true}"));

        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/test").build();
        ClockifyResponse<String> response = transport.send(request);

        assertEquals(200, response.statusCode());
        assertEquals("{\"success\":true}", response.body());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertEquals("/v1/test", recordedRequest.getPath());
        assertEquals("test-agent", recordedRequest.getHeader("User-Agent"));
        assertEquals("test-key", recordedRequest.getHeader("X-Api-Key"));
        assertEquals("custom-value", recordedRequest.getHeader("X-Custom"));
    }

    @Test
    void testSendBinaryResponse() throws Exception {
        byte[] mockData = new byte[]{1, 2, 3, 4, 5};
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/octet-stream")
                .setBody(new okio.Buffer().write(mockData)));

        ClockifyRequest request = ClockifyRequest.builder("GET", "/v1/file")
                .binaryResponse(true)
                .build();
        ClockifyBinaryResponse response = transport.sendBinary(request);

        assertEquals(200, response.statusCode());
        assertArrayEquals(mockData, response.bytes());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("GET", recordedRequest.getMethod());
        assertEquals("/v1/file", recordedRequest.getPath());
        assertEquals("application/octet-stream", recordedRequest.getHeader("Accept"));
    }

    @Test
    void testSendPostWithJsonBody() throws Exception {
        mockWebServer.enqueue(new MockResponse().setResponseCode(201).setBody("{}"));

        ClockifyRequest request = ClockifyRequest.builder("POST", "/v1/post")
                .jsonBody("{\"key\":\"value\"}")
                .build();
        
        ClockifyResponse<String> response = transport.send(request);

        assertEquals(201, response.statusCode());

        RecordedRequest recordedRequest = mockWebServer.takeRequest();
        assertEquals("POST", recordedRequest.getMethod());
        assertEquals("/v1/post", recordedRequest.getPath());
        assertEquals("application/json", recordedRequest.getHeader("Content-Type"));
        assertEquals("{\"key\":\"value\"}", recordedRequest.getBody().readUtf8());
    }
}
