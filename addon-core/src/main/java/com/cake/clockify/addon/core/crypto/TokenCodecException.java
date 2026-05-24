package com.cake.clockify.addon.core.crypto;

public class TokenCodecException extends RuntimeException {

    public TokenCodecException(String message) {
        super(message);
    }

    public TokenCodecException(String message, Throwable cause) {
        super(message, cause);
    }
}
