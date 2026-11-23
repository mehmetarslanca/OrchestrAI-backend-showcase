package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum CapabilityRisk {
    LOW("low", 10),
    MODERATE("moderate", 8),
    ELEVATED("elevated", 6),
    HIGH("high", 4),
    CRITICAL("critical", 2),
    UNKNOWN("unknown", 6);

    private final String value;
    private final int modelCompatibilityScore;

    CapabilityRisk(final String value, final int modelCompatibilityScore) {
        this.value = value;
        this.modelCompatibilityScore = modelCompatibilityScore;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public int modelCompatibilityScore() {
        return modelCompatibilityScore;
    }

    @JsonCreator
    public static CapabilityRisk fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final CapabilityRisk risk : values()) {
            if (risk.value.equals(normalizedValue)) {
                return risk;
            }
        }
        return UNKNOWN;
    }
}
