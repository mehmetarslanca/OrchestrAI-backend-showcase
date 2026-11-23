package com.arslanca.orchestrai.business.enums;

import java.util.Locale;

public enum GenerationFailureReason {
    USER_CANCELLED,
    CLIENT_DISCONNECTED,
    CLIENT_TIMEOUT,
    PING_FAILED,
    SEMAPHORE_UNAVAILABLE,
    CONCURRENCY_LIMIT,
    WORKER_FAILURE,
    PIPELINE_FAILURE,
    VALIDATION_FAILURE;

    public String metadataValue() {
        return name();
    }

    public static GenerationFailureReason fromMetadataValue(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        final String normalized = value.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .replaceAll("[^A-Za-z0-9_]", "")
                .toUpperCase(Locale.ROOT);

        return switch (normalized) {
            case "USER_CANCELLED" -> USER_CANCELLED;
            case "CLIENT_DISCONNECTED", "PUBLISHER_ERROR" -> CLIENT_DISCONNECTED;
            case "CLIENT_TIMEOUT" -> CLIENT_TIMEOUT;
            case "PING_FAILED" -> PING_FAILED;
            case "SEMAPHORE_UNAVAILABLE", "SEMAPHOREUNAVAILABLE" -> SEMAPHORE_UNAVAILABLE;
            case "CONCURRENCY_LIMIT", "CONCURRENCYLIMIT" -> CONCURRENCY_LIMIT;
            case "WORKER_FAILURE", "EXECUTORREJECTION", "STALEPENDINGRECOVERY", "STALEPROCESSINGRECOVERY" -> WORKER_FAILURE;
            case "PIPELINE_FAILURE", "PIPELINEFAILURE", "PREPAREDPIPELINEFAILURE" -> PIPELINE_FAILURE;
            case "VALIDATION_FAILURE", "VALIDATIONFAILURE" -> VALIDATION_FAILURE;
            default -> throw new IllegalArgumentException("Unknown generation failure reason: " + value);
        };
    }
}
