package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlanningResult;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;

public interface BlueprintPlanningService {

    BlueprintPlanningResult planBlueprint(
            StructuredAnalysis analysis,
            String ideTarget,
            ResolvedHeavyModel resolvedHeavyModel,
            StarterSelection starterSelection,
            PlanningModelPolicy planningModelPolicy);

    default BlueprintPlanningResult planBlueprint(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final PlanningModelPolicy planningModelPolicy,
            final GenerationUsageContext generationUsageContext) {
        return planBlueprint(analysis, ideTarget, resolvedHeavyModel, starterSelection, planningModelPolicy);
    }
}
