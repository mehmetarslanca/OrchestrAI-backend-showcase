package com.arslanca.orchestrai.core.exception.type;

import java.time.OffsetDateTime;

public record ApiErrorResponse(
        int code,
        String error,
        String message,
        OffsetDateTime timestamp) {
    public static ApiErrorResponse of(final int code, final String error, final String message) {
        return new ApiErrorResponse(code, error, message, OffsetDateTime.now());
    }
}
