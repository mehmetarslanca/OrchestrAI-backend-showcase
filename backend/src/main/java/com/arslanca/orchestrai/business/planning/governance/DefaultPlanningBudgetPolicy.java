package com.arslanca.orchestrai.business.planning.governance;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import org.springframework.stereotype.Component;

@Component
public class DefaultPlanningBudgetPolicy implements PlanningBudgetPolicy {

    @Override
    public PlanningBudgetDecision decide(final PlanningGovernanceContext context) {
        final PlanningComplexity complexity = context == null || context.structuredAnalysis() == null
                ? PlanningComplexity.MODERATE
                : context.structuredAnalysis().planningComplexity();
        final boolean localModel = context != null
                && context.planningModelPolicy() != null
                && context.planningModelPolicy().localModel();
        final ModelCapability capability = context == null || context.planningModelPolicy() == null
                ? ModelCapability.UNKNOWN
                : context.planningModelPolicy().modelCapability();
        final DegradeStrategy degradeStrategy = context == null || context.planningModelPolicy() == null
                ? DegradeStrategy.NONE
                : context.planningModelPolicy().degradeStrategy();

        return switch (complexity) {
            case SIMPLE -> new PlanningBudgetDecision(true, "simple_reduced", 1, 0, false, true, "");
            case MODERATE -> new PlanningBudgetDecision(true, "moderate_partial", 2, 1, true, true, "");
            case COMPLEX -> new PlanningBudgetDecision(true, "complex_targeted", 2, 1, true, true, "");
            case ENTERPRISE -> enterpriseDecision(localModel, capability, degradeStrategy);
        };
    }

    @Override
    public String policyVersion() {
        return "budget-v2";
    }

    private PlanningBudgetDecision enterpriseDecision(
            final boolean localModel,
            final ModelCapability capability,
            final DegradeStrategy degradeStrategy) {
        if (degradeStrategy == DegradeStrategy.ENTERPRISE_QUALITY_WARNING
                || degradeStrategy.blocksPreviewGeneration()
                || (localModel && capability == ModelCapability.LOW)) {
            return new PlanningBudgetDecision(
                    true,
                    "enterprise_guarded",
                    0,
                    0,
                    false,
                    false,
                    "reduced_planning_budget");
        }
        return new PlanningBudgetDecision(true, "enterprise_guarded", 1, 0, true, false, "");
    }
}
