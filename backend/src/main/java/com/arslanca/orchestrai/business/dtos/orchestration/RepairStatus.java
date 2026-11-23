package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum RepairStatus {
    NOT_REQUIRED("not_required"),
    COMPLETED("completed"),
    REJECTED("rejected"),
    FAILED("failed"),
    INVALIDATED("invalidated"),
    UNKNOWN("unknown");

    private final String value;

    RepairStatus(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static RepairStatus fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final RepairStatus status : values()) {
            if (status.value.equals(normalizedValue)) {
                return status;
            }
        }
        return UNKNOWN;
    }
}
