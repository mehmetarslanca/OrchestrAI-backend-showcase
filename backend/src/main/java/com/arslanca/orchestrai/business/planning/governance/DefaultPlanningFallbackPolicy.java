package com.arslanca.orchestrai.business.planning.governance;

import org.springframework.stereotype.Component;

@Component
public class DefaultPlanningFallbackPolicy implements PlanningFallbackPolicy {

    @Override
    public PlanningFallbackDecision evaluateAfterRepair(
            final PlanningBudgetDecision budgetDecision,
            final int noImprovementCount) {
        if (budgetDecision == null) {
            return PlanningFallbackDecision.allowContinue();
        }
        if (noImprovementCount > budgetDecision.maxNoImprovementAttempts()) {
            return PlanningFallbackDecision.stop("no_quality_improvement");
        }
        return PlanningFallbackDecision.allowContinue();
    }
}
