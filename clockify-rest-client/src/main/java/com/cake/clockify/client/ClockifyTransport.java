package com.cake.clockify.client;

import java.io.IOException;

public interface ClockifyTransport {
    ClockifyResponse<String> send(ClockifyRequest request) throws IOException, InterruptedException;

    ClockifyBinaryResponse sendBinary(ClockifyRequest request) throws IOException, InterruptedException;
}
