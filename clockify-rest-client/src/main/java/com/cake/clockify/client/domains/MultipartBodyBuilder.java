package com.cake.clockify.client.domains;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Pattern;

final class MultipartBodyBuilder {
    private static final Pattern FIELD_NAME = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern CONTENT_TYPE = Pattern.compile("[a-z0-9!#$&^_.+-]+/[a-z0-9!#$&^_.+-]+");

    private MultipartBodyBuilder() {
    }

    static void writeField(
            ByteArrayOutputStream out,
            String boundary,
            String name,
            String value) throws IOException {
        String actualName = requireFieldName(name);
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + actualName
                + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    static void writeFile(
            ByteArrayOutputStream out,
            String boundary,
            String fieldName,
            String fileName,
            String contentType,
            byte[] bytes) throws IOException {
        String actualFieldName = requireFieldName(fieldName);
        String actualFileName = safeFileName(fileName);
        String actualContentType = safeContentType(contentType);
        out.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + actualFieldName
                + "\"; filename=\"" + actualFileName + "\"\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + actualContentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(bytes);
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    static void finish(ByteArrayOutputStream out, String boundary) throws IOException {
        out.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private static String requireFieldName(String name) {
        if (name == null || !FIELD_NAME.matcher(name).matches()) {
            throw new IllegalArgumentException(
                    "multipart field name must contain only letters, digits, underscore, dot, or hyphen");
        }
        return name;
    }

    private static String safeFileName(String fileName) {
        String cleaned = fileName == null ? "" : fileName.trim().replace('\\', '/');
        int lastSlash = cleaned.lastIndexOf('/');
        if (lastSlash >= 0) {
            cleaned = cleaned.substring(lastSlash + 1);
        }
        cleaned = cleaned.replaceAll("[\\p{Cntrl}\";:]", "_")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "file";
        }
        return cleaned.length() <= 120 ? cleaned : cleaned.substring(0, 120);
    }

    private static String safeContentType(String contentType) {
        String cleaned = contentType == null ? "" : contentType.trim().toLowerCase(Locale.ROOT);
        if (cleaned.isBlank() || !CONTENT_TYPE.matcher(cleaned).matches()) {
            return "application/octet-stream";
        }
        return cleaned;
    }
}
