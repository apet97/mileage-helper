package com.cake.clockify.client;

import com.cake.clockify.client.auth.AddonTokenAuthProvider;
import com.cake.clockify.client.auth.ApiKeyAuthProvider;
import com.cake.clockify.client.auth.ClockifyAuthProvider;

import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ClockifyClientBuilder {
    private URI backendBaseUrl;
    private URI reportsBaseUrl;
    private ClockifyAuthProvider authProvider;
    private String workspaceId;
    private String userAgent;
    private final Map<String, String> customHeaders = new LinkedHashMap<>();
    private ClockifyRetryPolicy retryPolicy = ClockifyRetryPolicy.none();
    private Duration requestTimeout = ClockifyClientConfig.DEFAULT_TIMEOUT;
    private int maxResponseBytes = ClockifyClientConfig.DEFAULT_MAX_RESPONSE_BYTES;
    private boolean followRedirects = false;
    private boolean allowCrossHostRedirects = false;
    private ClockifyTransport transport;

    public ClockifyClientBuilder backendBaseUrl(URI backendBaseUrl) { this.backendBaseUrl = backendBaseUrl; return this; }
    public ClockifyClientBuilder backendBaseUrl(String backendBaseUrl) { this.backendBaseUrl = backendBaseUrl == null ? null : URI.create(backendBaseUrl); return this; }
    public ClockifyClientBuilder reportsBaseUrl(URI reportsBaseUrl) { this.reportsBaseUrl = reportsBaseUrl; return this; }
    public ClockifyClientBuilder reportsBaseUrl(String reportsBaseUrl) { this.reportsBaseUrl = reportsBaseUrl == null ? null : URI.create(reportsBaseUrl); return this; }
    public ClockifyClientBuilder authProvider(ClockifyAuthProvider authProvider) { this.authProvider = authProvider; return this; }
    public ClockifyClientBuilder apiKey(String apiKey) { return authProvider(new ApiKeyAuthProvider(apiKey)); }
    public ClockifyClientBuilder addonToken(String addonToken) { return authProvider(new AddonTokenAuthProvider(addonToken)); }
    public ClockifyClientBuilder workspaceId(String workspaceId) { this.workspaceId = workspaceId; return this; }
    public ClockifyClientBuilder userAgent(String userAgent) { this.userAgent = userAgent; return this; }
    public ClockifyClientBuilder header(String name, String value) { this.customHeaders.put(name, value); return this; }
    public ClockifyClientBuilder retryPolicy(ClockifyRetryPolicy retryPolicy) { this.retryPolicy = retryPolicy; return this; }
    public ClockifyClientBuilder requestTimeout(Duration requestTimeout) { this.requestTimeout = requestTimeout; return this; }
    public ClockifyClientBuilder maxResponseBytes(int maxResponseBytes) { this.maxResponseBytes = maxResponseBytes; return this; }
    public ClockifyClientBuilder followRedirects(boolean followRedirects) { this.followRedirects = followRedirects; return this; }
    public ClockifyClientBuilder allowCrossHostRedirects(boolean allowCrossHostRedirects) { this.allowCrossHostRedirects = allowCrossHostRedirects; return this; }
    public ClockifyClientBuilder transport(ClockifyTransport transport) { this.transport = transport; return this; }

    public ClockifyClientConfig buildConfig() {
        if (backendBaseUrl == null) {
            throw new IllegalStateException("backendBaseUrl is required");
        }
        return new ClockifyClientConfig(backendBaseUrl, reportsBaseUrl, authProvider,
                workspaceId, userAgent, customHeaders, retryPolicy, requestTimeout, maxResponseBytes,
                followRedirects, allowCrossHostRedirects);
    }

    public ClockifyClient build() {
        ClockifyClientConfig config = buildConfig();
        return transport != null ? new ClockifyClient(config, transport) : new ClockifyClient(config);
    }
}
