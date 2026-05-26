package com.cake.clockify.client;

final class TestClockifyClient {
    private static final String BACKEND_URL = "https://backend.example.test/api";
    private static final String REPORTS_URL = "https://reports.example.test";

    private TestClockifyClient() {
    }

    static ClockifyClientConfig config() {
        return ClockifyClient.builder()
                .apiKey("secret")
                .backendBaseUrl(BACKEND_URL)
                .reportsBaseUrl(REPORTS_URL)
                .buildConfig();
    }

    static ClockifyClientConfig addonConfig() {
        return ClockifyClient.builder()
                .addonToken("secret")
                .backendBaseUrl(BACKEND_URL)
                .reportsBaseUrl(REPORTS_URL)
                .buildConfig();
    }

    static ClockifyClient client(ClockifyTransport transport) {
        return new ClockifyClient(config(), transport);
    }

    static ClockifyClient addonClient(ClockifyTransport transport) {
        return new ClockifyClient(addonConfig(), transport);
    }
}
