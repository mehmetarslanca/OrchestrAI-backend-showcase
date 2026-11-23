package com.arslanca.orchestrai.core.exception.type;

import org.springframework.http.HttpStatus;

public class HeavyModelResolutionException extends RuntimeException {

    private final HttpStatus status;
    private final String error;

    private HeavyModelResolutionException(final HttpStatus status, final String error, final String message) {
        super(message);
        this.status = status;
        this.error = error;
    }

    public static HeavyModelResolutionException badRequest(final String message) {
        return new HeavyModelResolutionException(HttpStatus.BAD_REQUEST, "HEAVY_MODEL_INVALID", message);
    }

    public static HeavyModelResolutionException serviceUnavailable(final String message) {
        return new HeavyModelResolutionException(HttpStatus.SERVICE_UNAVAILABLE, "HEAVY_MODEL_UNAVAILABLE", message);
    }

    public HttpStatus status() {
        return status;
    }

    public String error() {
        return error;
    }
}
