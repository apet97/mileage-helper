package com.cake.clockify.client;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    @Test
    void uploadImageCapsVeryLongFilenames() throws Exception {
        ClientsClientTest.RecordingTransport transport = new ClientsClientTest.RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);
        String longName = "a".repeat(180) + ".png";

        client.files().uploadImage("/tmp/uploads/" + longName, "image/png", new byte[]{1, 2});

        String fileName = multipartFileName(transport);
        assertEquals(120, fileName.length());
        assertFalse(fileName.contains("/"));
        assertFalse(fileName.contains(longName));
    }

    @Test
    void uploadImageReducesWindowsAndUnixPathsToBasename() throws Exception {
        ClientsClientTest.RecordingTransport transport = new ClientsClientTest.RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.files().uploadImage("C:\\Users\\me\\Downloads/nested/receipt.png", "image/png", new byte[]{1, 2});

        String body = multipartBody(transport);
        assertTrue(body.contains("filename=\"receipt.png\""));
        assertFalse(body.contains("C:"));
        assertFalse(body.contains("Users"));
        assertFalse(body.contains("Downloads"));
        assertFalse(body.contains("nested"));
    }

    @Test
    void uploadImageNeutralizesFilenameSeparatorsAndInvalidContentType() throws Exception {
        ClientsClientTest.RecordingTransport transport = new ClientsClientTest.RecordingTransport(List.of("{}"));
        ClockifyClient client = TestClockifyClient.client(transport);

        client.files().uploadImage("receipt\";:bad\u0007.png", "image/png; charset=utf-8", new byte[]{1, 2});

        String body = multipartBody(transport);
        assertTrue(body.contains("filename=\"receipt___bad_.png\""));
        assertTrue(body.contains("Content-Type: application/octet-stream"));
        String fileHeader = fileContentDisposition(body);
        assertFalse(fileHeader.contains(";:"));
        assertFalse(fileHeader.contains("\u0007"));
    }

    private static String multipartBody(ClientsClientTest.RecordingTransport transport) {
        return new String(transport.requests.get(0).bytesBody(), StandardCharsets.UTF_8);
    }

    private static String multipartFileName(ClientsClientTest.RecordingTransport transport) {
        String header = fileContentDisposition(multipartBody(transport));
        String marker = "filename=\"";
        int start = header.indexOf(marker) + marker.length();
        int end = header.indexOf('"', start);
        return header.substring(start, end);
    }

    private static String fileContentDisposition(String body) {
        return body.lines()
                .filter(line -> line.startsWith("Content-Disposition:") && line.contains("filename=\""))
                .findFirst()
                .orElseThrow();
    }
}
