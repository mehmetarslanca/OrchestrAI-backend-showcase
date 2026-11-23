package com.arslanca.orchestrai.business.dtos.orchestration;

public enum PlanningComplexity {
    SIMPLE,
    MODERATE,
    COMPLEX,
    ENTERPRISE;

    public boolean isAtLeast(final PlanningComplexity other) {
        if (other == null) {
            return true;
        }
        return ordinal() >= other.ordinal();
    }

    public static PlanningComplexity max(
            final PlanningComplexity first,
            final PlanningComplexity second) {
        final PlanningComplexity normalizedFirst = first == null ? MODERATE : first;
        final PlanningComplexity normalizedSecond = second == null ? MODERATE : second;
        return normalizedFirst.ordinal() >= normalizedSecond.ordinal() ? normalizedFirst : normalizedSecond;
    }

    public static PlanningComplexity fromValue(final String value) {
        if (value == null || value.isBlank()) {
            return MODERATE;
        }

        final String normalizedValue = value.trim().toLowerCase();
        return switch (normalizedValue) {
            case "simple", "low" -> SIMPLE;
            case "moderate", "medium" -> MODERATE;
            case "complex", "high" -> COMPLEX;
            case "enterprise" -> ENTERPRISE;
            default -> MODERATE;
        };
    }

    public boolean requiresPreviewReview() {
        return this == COMPLEX || this == ENTERPRISE;
    }

    public PlanningMode recommendedPlanningMode() {
        return switch (this) {
            case SIMPLE -> PlanningMode.SINGLE_PASS;
            case MODERATE -> PlanningMode.REDUCED_MULTI_PASS;
            case COMPLEX, ENTERPRISE -> PlanningMode.MULTI_PASS_EXPANSION;
        };
    }
}
