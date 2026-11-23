package com.arslanca.orchestrai.business.planning.governance;

public interface PreviewCachePolicy {
    PreviewCacheDecision buildDecision(PlanningGovernanceContext context);

    String buildPreviewCacheKey(PlanningGovernanceContext context);

    String policyVersion();
}
