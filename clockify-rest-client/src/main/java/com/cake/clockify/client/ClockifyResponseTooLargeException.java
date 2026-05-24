package com.cake.clockify.client;

import java.io.IOException;

public final class ClockifyResponseTooLargeException extends IOException {
    public ClockifyResponseTooLargeException(String message) {
        super(message);
    }
}
