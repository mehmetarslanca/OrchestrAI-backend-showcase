package com.arslanca.orchestrai.business.planning.preview;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;

public record PreviewGenerationContext(
        ProjectGenerationPreparationEntity preparation,
        StructuredAnalysis structuredAnalysis,
        DeterministicPlanningProfile planningProfile,
        StarterSelection selectedStarterSelection,
        ResolvedHeavyModel resolvedHeavyModel,
        PlanningModelPolicy planningModelPolicy) {
}
