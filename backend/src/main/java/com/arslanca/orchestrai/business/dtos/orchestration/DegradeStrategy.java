package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum DegradeStrategy {
    NONE("none"),
    PLANNER_MODEL_UNAVAILABLE("planner_model_unavailable"),
    STRONGER_MODEL_REQUIRED("stronger_model_required"),
    ENTERPRISE_QUALITY_WARNING("enterprise_quality_warning"),
    SKELETON_FIRST_PREVIEW("skeleton_first_preview"),
    REDUCED_PREVIEW("reduced_preview"),
    UNKNOWN("unknown");

    private final String value;

    DegradeStrategy(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static DegradeStrategy fromValue(final String text) {
        if (text == null || text.isBlank()) {
            return NONE;
        }

        final String normalizedText = text.trim().toLowerCase(Locale.ROOT);
        for (final DegradeStrategy strategy : values()) {
            if (strategy.value.equals(normalizedText)) {
                return strategy;
            }
        }
        return UNKNOWN;
    }

    public boolean blocksPreviewGeneration() {
        return this == STRONGER_MODEL_REQUIRED;
    }
}
