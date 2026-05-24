package com.cake.clockify.client.domains;

import com.cake.clockify.client.ClockifyBaseUrlFamily;
import com.cake.clockify.client.ClockifyRequest;
import com.cake.clockify.client.ClockifyTransport;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.UUID;

public final class FilesClient {
    private final ClockifyTransport transport;
    private final ObjectMapper objectMapper;

    public FilesClient(ClockifyTransport transport, ObjectMapper objectMapper) {
        this.transport = Objects.requireNonNull(transport, "transport");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    public JsonNode uploadImage(String fileName, String contentType, byte[] bytes) throws IOException, InterruptedException {
        if (fileName == null || fileName.isBlank()) throw new IllegalArgumentException("fileName must not be blank");
        if (contentType == null || contentType.isBlank()) throw new IllegalArgumentException("contentType must not be blank");
        Objects.requireNonNull(bytes, "bytes");
        String boundary = "clockify-rest-client-" + UUID.randomUUID();
        byte[] body = multipartFile(boundary, "file", fileName, contentType, bytes);
        ClockifyRequest request = ClockifyRequest.builder("POST", "/v1/file/image")
                .baseUrlFamily(ClockifyBaseUrlFamily.BACKEND)
                .bytesBody(body, "multipart/form-data; boundary=" + boundary)
                .build();
        return objectMapper.readTree(transport.send(request).body());
    }

    private static byte[] multipartFile(String boundary, String field, String fileName, String contentType, byte[] bytes) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + field + "\"; filename=\"" + fileName.replace("\"", "") + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }
}
