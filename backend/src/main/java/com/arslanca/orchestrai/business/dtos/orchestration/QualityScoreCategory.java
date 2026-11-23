package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

public enum QualityScoreCategory {
    STRUCTURAL_COMPLETENESS("structuralCompleteness"),
    SCOPE_COVERAGE("scopeCoverage"),
    PHASE_DEPTH("phaseDepth"),
    RISK_TEST_SECURITY_COVERAGE("riskTestSecurityCoverage"),
    MODEL_COMPATIBILITY("modelCompatibility"),
    COST_EFFICIENCY_SIGNAL("costEfficiencySignal"),
    UNKNOWN("unknown");

    private final String value;

    QualityScoreCategory(final String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static QualityScoreCategory fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }

        final String normalizedValue = value.trim().toLowerCase(Locale.ROOT);
        for (final QualityScoreCategory category : values()) {
            if (category.value.toLowerCase(Locale.ROOT).equals(normalizedValue)) {
                return category;
            }
        }
        return UNKNOWN;
    }
}
