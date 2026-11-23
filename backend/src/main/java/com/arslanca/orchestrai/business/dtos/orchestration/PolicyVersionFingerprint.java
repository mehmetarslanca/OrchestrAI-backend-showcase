package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.Locale;

public record PolicyVersionFingerprint(String value) {

    public PolicyVersionFingerprint {
        value = value == null || value.isBlank() ? "unknown" : value.trim();
    }

    public static PolicyVersionFingerprint from(
            final PlanningMode planningMode,
            final CapabilityRisk capabilityRisk,
            final boolean previewRecommended,
            final boolean previewRequired,
            final PlannerModelRef plannerModelRef,
            final DegradeStrategy degradeStrategy) {
        final PlanningMode normalizedPlanningMode = planningMode == null ? PlanningMode.SINGLE_PASS : planningMode;
        final CapabilityRisk normalizedCapabilityRisk = capabilityRisk == null ? CapabilityRisk.UNKNOWN : capabilityRisk;
        final PlannerModelRef normalizedPlannerModelRef = plannerModelRef == null ? PlannerModelRef.unknown() : plannerModelRef;
        final DegradeStrategy normalizedDegradeStrategy = degradeStrategy == null ? DegradeStrategy.NONE : degradeStrategy;

        return new PolicyVersionFingerprint(String.join(
                ":",
                normalizedPlanningMode.name().toLowerCase(Locale.ROOT),
                normalizedCapabilityRisk.value(),
                previewRecommended ? "preview-recommended" : "preview-optional",
                previewRequired ? "preview-required" : "preview-not-required",
                normalizedPlannerModelRef.stableValue(),
                normalizedDegradeStrategy.value()));
    }

    public static PolicyVersionFingerprint unknown() {
        return new PolicyVersionFingerprint("unknown");
    }
}
