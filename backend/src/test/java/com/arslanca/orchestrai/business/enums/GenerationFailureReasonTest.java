package com.arslanca.orchestrai.business.enums;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GenerationFailureReasonTest {

    @ParameterizedTest
    @CsvSource({
            "USER_CANCELLED, USER_CANCELLED",
            "CLIENT_DISCONNECTED, CLIENT_DISCONNECTED",
            "PUBLISHER_ERROR, CLIENT_DISCONNECTED",
            "CLIENT_TIMEOUT, CLIENT_TIMEOUT",
            "PING_FAILED, PING_FAILED",
            "SEMAPHORE_UNAVAILABLE, SEMAPHORE_UNAVAILABLE",
            "SEMAPHOREUNAVAILABLE, SEMAPHORE_UNAVAILABLE",
            "CONCURRENCY_LIMIT, CONCURRENCY_LIMIT",
            "CONCURRENCYLIMIT, CONCURRENCY_LIMIT",
            "WORKER_FAILURE, WORKER_FAILURE",
            "EXECUTORREJECTION, WORKER_FAILURE",
            "STALEPENDINGRECOVERY, WORKER_FAILURE",
            "STALEPROCESSINGRECOVERY, WORKER_FAILURE",
            "PIPELINE_FAILURE, PIPELINE_FAILURE",
            "PIPELINEFAILURE, PIPELINE_FAILURE",
            "PREPAREDPIPELINEFAILURE, PIPELINE_FAILURE",
            "VALIDATION_FAILURE, VALIDATION_FAILURE",
            "VALIDATIONFAILURE, VALIDATION_FAILURE",
            "user-cancelled, USER_CANCELLED",
            " client disconnected , CLIENT_DISCONNECTED",
            "publisher error, CLIENT_DISCONNECTED"
    })
    void shouldMapValidMetadataValuesToEnum(String input, GenerationFailureReason expectedReason) {
        assertThat(GenerationFailureReason.fromMetadataValue(input)).isEqualTo(expectedReason);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnNullForBlankOrNullValues(String input) {
        assertThat(GenerationFailureReason.fromMetadataValue(input)).isNull();
    }

    @Test
    void shouldThrowIllegalArgumentExceptionForUnknownValue() {
        assertThatThrownBy(() -> GenerationFailureReason.fromMetadataValue("UNKNOWN_REASON"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unknown generation failure reason");
    }

    @Test
    void metadataValueShouldReturnName() {
        assertThat(GenerationFailureReason.USER_CANCELLED.metadataValue()).isEqualTo("USER_CANCELLED");
        assertThat(GenerationFailureReason.PIPELINE_FAILURE.metadataValue()).isEqualTo("PIPELINE_FAILURE");
    }
}
