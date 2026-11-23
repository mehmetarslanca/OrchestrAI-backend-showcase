package com.arslanca.orchestrai.business.planning.governance;

public interface PlanningRolloutPolicy {
    PlanningRolloutDecision resolveDecision(PlanningGovernanceContext context);

    String policyVersion();
}
