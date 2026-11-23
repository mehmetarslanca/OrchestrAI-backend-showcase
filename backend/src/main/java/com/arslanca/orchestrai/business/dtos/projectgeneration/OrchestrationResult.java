package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.Map;
import java.util.Objects;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;

public record OrchestrationResult(
        CanonicalBlueprint canonicalBlueprint,
        StarterSelection starterSelection,
        PlanningQualitySummary planningQualitySummary,
        PlanningRepairOutcome planningRepairOutcome) {

    public OrchestrationResult {
        canonicalBlueprint = Objects.requireNonNull(canonicalBlueprint, "canonicalBlueprint cannot be null");
        starterSelection = Objects.requireNonNull(starterSelection, "starterSelection cannot be null");
        planningQualitySummary = planningQualitySummary == null
                ? PlanningDecisionPayloadMapper.fromLegacyQualitySummary(Map.of())
                : planningQualitySummary;
        planningRepairOutcome = planningRepairOutcome == null
                ? PlanningDecisionPayloadMapper.fromLegacyRepairStatus(Map.of())
                : planningRepairOutcome;
    }

    public OrchestrationResult(
            final CanonicalBlueprint canonicalBlueprint,
            final StarterSelection starterSelection) {
        this(canonicalBlueprint, starterSelection, null, null);
    }
}
