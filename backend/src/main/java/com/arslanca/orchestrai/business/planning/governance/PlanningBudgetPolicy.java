package com.arslanca.orchestrai.business.planning.governance;

public interface PlanningBudgetPolicy {
    PlanningBudgetDecision decide(PlanningGovernanceContext context);

    String policyVersion();
}
