package com.cake.clockify.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FilesClientTest {

    @Test
    void uploadImageSanitizesMultipartHeaders() throws Exception {
        ClientsClientTest.RecordingTransport transport = new ClientsClientTest.RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.files().uploadImage(
                "../evil\r\nContent-Disposition: form-data; name=\"pwned\".png",
                "image/png\r\nX-Injected: yes",
                new byte[]{1, 2});

        String body = new String(transport.requests.get(0).bytesBody(), StandardCharsets.UTF_8);
        assertTrue(body.contains("filename=\"evil__Content-Disposition_ form-data_ name=_pwned_.png\""));
        assertTrue(body.contains("Content-Type: application/octet-stream"));
        assertFalse(body.contains("name=\"pwned\""));
        assertFalse(body.contains("X-Injected: yes"));
    }
}
