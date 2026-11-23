package com.arslanca.orchestrai.core.exception.type;

public class SecretEncryptionException extends RuntimeException {

    public SecretEncryptionException(final String message) {
        super(message);
    }

    public SecretEncryptionException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
