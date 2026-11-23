package com.arslanca.orchestrai.core.exception.type;

public class GoogleAuthenticationException extends RuntimeException {

    public GoogleAuthenticationException(final String message) {
        super(message);
    }

    public GoogleAuthenticationException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
