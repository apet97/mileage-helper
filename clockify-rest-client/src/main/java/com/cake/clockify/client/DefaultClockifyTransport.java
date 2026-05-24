package com.cake.clockify.client;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public final class DefaultClockifyTransport implements ClockifyTransport {
    private final ClockifyClientConfig config;
    private final HttpClient httpClient;

    public DefaultClockifyTransport(ClockifyClientConfig config) {
        this(config, HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(config.requestTimeout())
                .build());
    }

    DefaultClockifyTransport(ClockifyClientConfig config, HttpClient httpClient) {
        this.config = config;
        this.httpClient = httpClient;
    }

    @Override
    public ClockifyResponse<String> send(ClockifyRequest request) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = sendWithRedirectsAndRetries(request);
        byte[] bytes = readBounded(response.body(), config.maxResponseBytes());
        String body = new String(bytes, StandardCharsets.UTF_8);
        if (response.statusCode() >= 400) throw ClockifyApiException.forStatus(request.method(), request.path(), response.statusCode(), response.headers().map(), body);
        return new ClockifyResponse<>(response.statusCode(), response.headers().map(), body);
    }

    @Override
    public ClockifyBinaryResponse sendBinary(ClockifyRequest request) throws IOException, InterruptedException {
        HttpResponse<InputStream> response = sendWithRedirectsAndRetries(request);
        byte[] bytes = readBounded(response.body(), config.maxResponseBytes());
        if (response.statusCode() >= 400) throw ClockifyApiException.forStatus(request.method(), request.path(), response.statusCode(), response.headers().map(), new String(bytes, StandardCharsets.UTF_8));
        return new ClockifyBinaryResponse(response.statusCode(), response.headers().map(), bytes);
    }

    private HttpResponse<InputStream> sendWithRedirectsAndRetries(ClockifyRequest request) throws IOException, InterruptedException {
        int redirectCount = 0;
        ClockifyRequest currentRequest = request;
        URI currentUri = join(config.baseUrl(request.baseUrlFamily()), request.pathWithQuery());

        while (true) {
            HttpResponse<InputStream> response = sendWithRetries(currentRequest, currentUri);
            int status = response.statusCode();
            if (status >= 300 && status < 400) {
                if (!config.followRedirects()) {
                    return response;
                }
                String location = response.headers().firstValue("Location").orElse(null);
                if (location == null) {
                    return response;
                }
                redirectCount++;
                if (redirectCount > 5) {
                    response.body().close();
                    throw new ClockifyTransportException("Too many redirects: " + redirectCount, null);
                }
                URI nextUri = currentUri.resolve(location);

                // Downgrade check: HTTPS -> HTTP
                if ("https".equalsIgnoreCase(currentUri.getScheme()) && "http".equalsIgnoreCase(nextUri.getScheme())) {
                    response.body().close();
                    throw new ClockifyTransportException("HTTPS to HTTP downgrade redirect rejected", null);
                }

                // Cross-host check:
                if (!currentUri.getHost().equalsIgnoreCase(nextUri.getHost())) {
                    if (!config.allowCrossHostRedirects()) {
                        response.body().close();
                        throw new ClockifyTransportException("Cross-host redirect rejected: " + currentUri.getHost() + " -> " + nextUri.getHost(), null);
                    }
                }

                response.body().close();
                currentUri = nextUri;

                String nextMethod = currentRequest.method();
                if (status == 303 || status == 301 || status == 302) {
                    nextMethod = "GET";
                }

                final String finalMethod = nextMethod;
                currentRequest = ClockifyRequest.builder(finalMethod, currentUri.getPath())
                        .baseUrlFamily(currentRequest.baseUrlFamily())
                        .binaryResponse(currentRequest.binaryResponse())
                        .build();

                continue;
            }
            return response;
        }
    }

    private HttpResponse<InputStream> sendWithRetries(ClockifyRequest request, URI targetUri) throws IOException, InterruptedException {
        int attempt = 0;
        while (true) {
            attempt++;
            HttpResponse<InputStream> response;
            try {
                response = httpClient.send(buildRequest(request, targetUri), HttpResponse.BodyHandlers.ofInputStream());
            } catch (IOException e) {
                throw new ClockifyTransportException("Clockify transport failed for " + request.method() + " " + targetUri, e);
            }
            if (!config.retryPolicy().shouldRetry(response.statusCode(), attempt)) return response;
            response.body().close();
            Thread.sleep(delayMillis(response, attempt));
        }
    }

    private HttpRequest buildRequest(ClockifyRequest request, URI targetUri) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(targetUri).timeout(config.requestTimeout());
        config.authProvider().apply(builder);
        builder.header("User-Agent", config.userAgent());
        config.customHeaders().forEach(builder::header);
        builder.header("Accept", request.binaryResponse() ? "application/octet-stream" : "application/json");
        if (request.bytesBody() != null && !"GET".equalsIgnoreCase(request.method())) {
            if (request.contentType() != null) builder.header("Content-Type", request.contentType());
            builder.method(request.method(), HttpRequest.BodyPublishers.ofByteArray(request.bytesBody()));
        } else if (request.jsonBody() != null && !"GET".equalsIgnoreCase(request.method())) {
            builder.header("Content-Type", request.contentType() == null ? "application/json" : request.contentType());
            builder.method(request.method(), HttpRequest.BodyPublishers.ofString(request.jsonBody(), StandardCharsets.UTF_8));
        } else {
            builder.method(request.method(), HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    static URI join(URI baseUrl, String pathWithQuery) {
        String base = baseUrl.toString();
        String path = pathWithQuery.startsWith("/") ? pathWithQuery : "/" + pathWithQuery;
        return URI.create(base + path);
    }

    private long delayMillis(HttpResponse<InputStream> response, int attempt) {
        String retryAfter = response.headers().firstValue("Retry-After").orElse(null);
        if (retryAfter != null) {
            try {
                return Math.min(Long.parseLong(retryAfter.trim()) * 1000L, config.retryPolicy().maxDelay().toMillis());
            } catch (NumberFormatException ignored) {
                // fall back to policy delay
            }
        }
        return config.retryPolicy().delayForAttempt(attempt).toMillis();
    }

    static byte[] readBounded(InputStream in, int maxBytes) throws IOException {
        try (InputStream input = in; ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            int read;
            while ((read = input.read(buffer)) != -1) {
                total += read;
                if (total > maxBytes) throw new ClockifyResponseTooLargeException("Clockify response exceeded maxResponseBytes=" + maxBytes);
                out.write(buffer, 0, read);
            }
            return out.toByteArray();
        }
    }
}
