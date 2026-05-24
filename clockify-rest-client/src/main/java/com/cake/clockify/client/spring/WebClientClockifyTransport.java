package com.cake.clockify.client.spring;

import com.cake.clockify.client.*;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class WebClientClockifyTransport implements ClockifyTransport {

    private final WebClient webClient;
    private final ClockifyClientConfig config;

    public WebClientClockifyTransport(ClockifyClientConfig config, WebClient.Builder webClientBuilder) {
        this.config = config;
        this.webClient = webClientBuilder.build();
    }

    @Override
    public ClockifyResponse<String> send(ClockifyRequest request) throws IOException, InterruptedException {
        return sendInternal(request).block();
    }

    @Override
    public ClockifyBinaryResponse sendBinary(ClockifyRequest request) throws IOException, InterruptedException {
        return sendBinaryInternal(request).block();
    }

    public Mono<ClockifyResponse<String>> sendInternal(ClockifyRequest request) {
        URI targetUri = buildUri(request);
        return executeRequest(request, targetUri, response -> {
            return response.bodyToMono(String.class)
                    .defaultIfEmpty("")
                    .map(body -> {
                        if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
                            throw ClockifyApiException.forStatus(request.method(), request.path(), response.statusCode().value(), response.headers().asHttpHeaders(), body);
                        }
                        return new ClockifyResponse<>(response.statusCode().value(), response.headers().asHttpHeaders(), body);
                    });
        });
    }

    public Mono<ClockifyBinaryResponse> sendBinaryInternal(ClockifyRequest request) {
        URI targetUri = buildUri(request);
        return executeRequest(request, targetUri, response -> {
            return DataBufferUtils.join(response.bodyToFlux(DataBuffer.class))
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        if (response.statusCode().is4xxClientError() || response.statusCode().is5xxServerError()) {
                            throw ClockifyApiException.forStatus(request.method(), request.path(), response.statusCode().value(), response.headers().asHttpHeaders(), new String(bytes));
                        }
                        return new ClockifyBinaryResponse(response.statusCode().value(), response.headers().asHttpHeaders(), bytes);
                    })
                    .defaultIfEmpty(new ClockifyBinaryResponse(response.statusCode().value(), response.headers().asHttpHeaders(), new byte[0]));
        });
    }

    private <T> Mono<T> executeRequest(ClockifyRequest request, URI targetUri, java.util.function.Function<ClientResponse, Mono<T>> responseExtractor) {
        return webClient.method(HttpMethod.valueOf(request.method()))
                .uri(targetUri)
                .headers(headers -> {
                    headers.set("User-Agent", config.userAgent());
                    headers.set("Accept", request.binaryResponse() ? "application/octet-stream" : "application/json");
                    config.customHeaders().forEach(headers::add);
                    
                    // Apply auth
                    if (config.authProvider() != null) {
                        headers.set(config.authProvider().headerName(), config.authProvider().headerValue());
                    }
                })
                .body((outputMessage, context) -> {
                    if (request.bytesBody() != null && !"GET".equalsIgnoreCase(request.method())) {
                        if (request.contentType() != null) outputMessage.getHeaders().setContentType(MediaType.parseMediaType(request.contentType()));
                        return outputMessage.writeWith(Mono.just(outputMessage.bufferFactory().wrap(request.bytesBody())));
                    } else if (request.jsonBody() != null && !"GET".equalsIgnoreCase(request.method())) {
                        outputMessage.getHeaders().setContentType(request.contentType() == null ? MediaType.APPLICATION_JSON : MediaType.parseMediaType(request.contentType()));
                        return outputMessage.writeWith(Mono.just(outputMessage.bufferFactory().wrap(request.jsonBody().getBytes())));
                    }
                    return outputMessage.setComplete();
                })
                .exchangeToMono(responseExtractor);
    }

    private URI buildUri(ClockifyRequest request) {
        String base = config.baseUrl(request.baseUrlFamily()).toString();
        String path = request.pathWithQuery();
        if (!path.startsWith("/")) path = "/" + path;
        return URI.create(base + path);
    }
}
