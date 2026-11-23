package com.arslanca.orchestrai.core.exception.type;

public class InfrastructureUnavailableException extends RuntimeException {

    public InfrastructureUnavailableException(final String message) {
        super(message);
    }

    public InfrastructureUnavailableException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
