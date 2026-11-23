package com.arslanca.orchestrai.business.planning.governance;

public interface PreviewBillingPolicy {
    PreviewBillingDecision resolveDecision(PlanningGovernanceContext context);

    String policyVersion();
}
