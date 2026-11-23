package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;

public interface MultiPassBlueprintPlannerService {

    CanonicalBlueprint generateBlueprint(
            StructuredAnalysis analysis,
            String ideTarget,
            ResolvedHeavyModel resolvedHeavyModel,
            StarterSelection starterSelection,
            boolean reducedMode);

    default CanonicalBlueprint generateBlueprint(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final boolean reducedMode,
            final GenerationUsageContext generationUsageContext) {
        return generateBlueprint(analysis, ideTarget, resolvedHeavyModel, starterSelection, reducedMode);
    }

    CanonicalBlueprint expandBlueprintDepth(
            StructuredAnalysis analysis,
            String ideTarget,
            ResolvedHeavyModel resolvedHeavyModel,
            StarterSelection starterSelection,
            CanonicalBlueprint currentPlan);

    default CanonicalBlueprint expandBlueprintDepth(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final CanonicalBlueprint currentPlan,
            final GenerationUsageContext generationUsageContext) {
        return expandBlueprintDepth(analysis, ideTarget, resolvedHeavyModel, starterSelection, currentPlan);
    }
}
