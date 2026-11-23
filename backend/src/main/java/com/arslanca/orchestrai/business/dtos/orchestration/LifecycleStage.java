package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum LifecycleStage {
    PREPARE("prepare"),
    PREVIEW("preview"),
    START("start"),
    UNKNOWN("unknown");

    private final String value;

    LifecycleStage(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static LifecycleStage fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final LifecycleStage stage : values()) {
            if (stage.value.equals(normalizedValue)) {
                return stage;
            }
        }
        return UNKNOWN;
    }
}
