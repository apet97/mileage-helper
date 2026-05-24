package com.cake.clockify.client;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ClockifyRequest {
    private final String method;
    private final String path;
    private final ClockifyBaseUrlFamily baseUrlFamily;
    private final Map<String, String> query;
    private final String jsonBody;
    private final byte[] bytesBody;
    private final String contentType;
    private final boolean binaryResponse;

    private ClockifyRequest(Builder b) {
        this.method = Objects.requireNonNull(b.method, "method");
        this.path = normalizePath(Objects.requireNonNull(b.path, "path"));
        this.baseUrlFamily = Objects.requireNonNull(b.baseUrlFamily, "baseUrlFamily");
        this.query = Collections.unmodifiableMap(new LinkedHashMap<>(b.query));
        this.jsonBody = b.jsonBody;
        this.bytesBody = b.bytesBody == null ? null : b.bytesBody.clone();
        this.contentType = b.contentType;
        this.binaryResponse = b.binaryResponse;
    }

    public String method() { return method; }
    public String path() { return path; }
    public ClockifyBaseUrlFamily baseUrlFamily() { return baseUrlFamily; }
    public Map<String, String> query() { return query; }
    public String jsonBody() { return jsonBody; }
    public byte[] bytesBody() { return bytesBody == null ? null : bytesBody.clone(); }
    public String contentType() { return contentType; }
    public boolean binaryResponse() { return binaryResponse; }

    public String pathWithQuery() {
        if (query.isEmpty()) return path;
        StringBuilder sb = new StringBuilder(path).append('?');
        boolean first = true;
        for (Map.Entry<String, String> e : query.entrySet()) {
            if (!first) sb.append('&');
            first = false;
            sb.append(enc(e.getKey())).append('=').append(enc(e.getValue()));
        }
        return sb.toString();
    }

    public static Builder builder(String method, String path) { return new Builder(method, path); }

    private static String normalizePath(String path) { return path.startsWith("/") ? path : "/" + path; }
    private static String enc(String v) { return URLEncoder.encode(v, StandardCharsets.UTF_8); }

    public static final class Builder {
        private final String method;
        private final String path;
        private ClockifyBaseUrlFamily baseUrlFamily = ClockifyBaseUrlFamily.BACKEND;
        private final Map<String, String> query = new LinkedHashMap<>();
        private String jsonBody;
        private byte[] bytesBody;
        private String contentType;
        private boolean binaryResponse;

        private Builder(String method, String path) { this.method = method; this.path = path; }
        public Builder baseUrlFamily(ClockifyBaseUrlFamily baseUrlFamily) { this.baseUrlFamily = baseUrlFamily; return this; }
        public Builder query(String name, Object value) { if (value != null) query.put(name, String.valueOf(value)); return this; }
        public Builder jsonBody(String jsonBody) { this.jsonBody = jsonBody; this.contentType = "application/json"; return this; }
        public Builder bytesBody(byte[] bytesBody, String contentType) { this.bytesBody = bytesBody == null ? null : bytesBody.clone(); this.contentType = contentType; return this; }
        public Builder binaryResponse(boolean binaryResponse) { this.binaryResponse = binaryResponse; return this; }
        public ClockifyRequest build() { return new ClockifyRequest(this); }
    }
}
