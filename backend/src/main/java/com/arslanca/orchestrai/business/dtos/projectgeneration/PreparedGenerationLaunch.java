package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.Map;
import java.util.UUID;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;

public record PreparedGenerationLaunch(
        UUID preparationId,
        UUID previewId,
        UUID chatId,
        Long chatMessageCount,
        String projectName,
        String ideTarget,
        String promptSummary,
        StructuredAnalysis structuredAnalysis,
        StarterSelection starterSelection,
        CanonicalBlueprint previewBlueprint,
        PlanningQualitySummary planningQualitySummary,
        PlanningRepairOutcome planningRepairOutcome) {

    public PreparedGenerationLaunch {
        planningQualitySummary = planningQualitySummary == null
                ? PlanningDecisionPayloadMapper.fromLegacyQualitySummary(Map.of())
                : planningQualitySummary;
        planningRepairOutcome = planningRepairOutcome == null
                ? PlanningDecisionPayloadMapper.fromLegacyRepairStatus(Map.of())
                : planningRepairOutcome;
    }
}
