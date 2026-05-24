package com.cake.clockify.client.auth;

import java.net.http.HttpRequest;
import java.util.Objects;

public final class AddonTokenAuthProvider implements ClockifyAuthProvider {
    private final String addonToken;

    public AddonTokenAuthProvider(String addonToken) {
        if (addonToken == null || addonToken.isBlank()) {
            throw new IllegalArgumentException("addonToken must not be blank");
        }
        this.addonToken = addonToken;
    }

    @Override
    public void apply(HttpRequest.Builder builder) {
        Objects.requireNonNull(builder, "builder").header(headerName(), addonToken);
    }

    @Override
    public String headerName() {
        return "X-Addon-Token";
    }

    @Override
    public String headerValue() {
        return addonToken;
    }

    @Override
    public String toString() {
        return "AddonTokenAuthProvider{headerName='X-Addon-Token', token='<redacted>'}";
    }
}
