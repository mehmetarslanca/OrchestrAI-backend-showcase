package com.arslanca.orchestrai.business.planning.governance;

public record PlanningFallbackDecision(boolean continueRepairs, String reason) {

    public PlanningFallbackDecision {
        reason = reason == null ? "" : reason.trim();
    }

    public static PlanningFallbackDecision allowContinue() {
        return new PlanningFallbackDecision(true, "");
    }

    public static PlanningFallbackDecision stop(final String reason) {
        return new PlanningFallbackDecision(false, reason);
    }
}
