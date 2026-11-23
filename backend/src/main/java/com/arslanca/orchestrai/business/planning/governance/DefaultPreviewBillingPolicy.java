package com.arslanca.orchestrai.business.planning.governance;

import org.springframework.stereotype.Component;

@Component
public class DefaultPreviewBillingPolicy implements PreviewBillingPolicy {

    @Override
    public PreviewBillingDecision resolveDecision(final PlanningGovernanceContext context) {
        return new PreviewBillingDecision(
                "preview_generated_not_billed",
                "preview_cache_hit_not_billed",
                "accepted_preview_reused_generation_billed_once",
                "fresh_planning_generation_billed_once",
                false);
    }

    @Override
    public String policyVersion() {
        return "billing-v2";
    }
}
