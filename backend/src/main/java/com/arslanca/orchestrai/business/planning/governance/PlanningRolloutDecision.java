package com.arslanca.orchestrai.business.planning.governance;

public record PlanningRolloutDecision(
        PlanningRolloutMode mode,
        boolean cohortMatched,
        boolean killSwitchActive,
        boolean enforced,
        String reason) {

    public PlanningRolloutDecision {
        mode = mode == null ? PlanningRolloutMode.OFF : mode;
        reason = reason == null ? "" : reason.trim();
    }
}
