package com.arslanca.orchestrai.business.planning.governance;

public interface PlanningFallbackPolicy {
    PlanningFallbackDecision evaluateAfterRepair(PlanningBudgetDecision budgetDecision, int noImprovementCount);
}
