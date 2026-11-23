package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum PlanningQualityFindingCode {
    UNDER_MIN_WORKSTREAM_COUNT("under_min_workstream_count"),
    UNDER_MIN_PHASE_COUNT("under_min_phase_count"),
    UNDER_MIN_AGENT_COUNT("under_min_agent_count"),
    MISSING_REQUIRED_WORKSTREAM_FAMILY("missing_required_workstream_family"),
    WORKSTREAM_WITHOUT_PHASE("workstream_without_phase"),
    STARTER_ARCHITECTURE_CONFLICT("starter_architecture_conflict"),
    MISSING_FRONTEND_OR_WORKER_COVERAGE("missing_frontend_or_worker_coverage"),
    IDE_RULES_CONFLICT("ide_rules_conflict"),
    WEAK_PHASE_DEPTH("weak_phase_depth"),
    WEAK_RISK_COVERAGE("weak_risk_coverage"),
    UNKNOWN("unknown");

    private final String value;

    PlanningQualityFindingCode(final String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    @JsonCreator
    public static PlanningQualityFindingCode fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final PlanningQualityFindingCode code : values()) {
            if (code.value.equals(normalizedValue)) {
                return code;
            }
        }
        return UNKNOWN;
    }
}
