package com.cake.clockify.client.auth;

import java.net.http.HttpRequest;

public interface ClockifyAuthProvider {
    void apply(HttpRequest.Builder builder);

    String headerName();

    String headerValue();
}
