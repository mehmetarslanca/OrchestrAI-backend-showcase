package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlanningQualityFindingType {
    SHAPE_DEFICIT("shape_deficit"),
    COVERAGE_DEFICIT("coverage_deficit"),
    DEPTH_DEFICIT("depth_deficit"),
    RISK_DEFICIT("risk_deficit"),
    AGENT_DEFICIT("agent_deficit"),
    RULES_DEFICIT("rules_deficit"),
    STARTER_CONFLICT("starter_conflict"),
    UNKNOWN("unknown");

    private final String value;

    PlanningQualityFindingType(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PlanningQualityFindingType fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final PlanningQualityFindingType type : values()) {
            if (type.value.equals(normalizedValue)) {
                return type;
            }
        }
        return UNKNOWN;
    }
}
