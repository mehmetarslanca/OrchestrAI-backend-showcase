package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.Map;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;

public record BlueprintPlanningResult(
        CanonicalBlueprint canonicalBlueprint,
        boolean accepted,
        PlanningQualitySummary qualitySummary,
        PlanningRepairOutcome repairStatus) {

    public BlueprintPlanningResult {
        qualitySummary = qualitySummary == null
                ? PlanningDecisionPayloadMapper.fromLegacyQualitySummary(Map.of())
                : qualitySummary;
        repairStatus = repairStatus == null
                ? PlanningDecisionPayloadMapper.fromLegacyRepairStatus(Map.of())
                : repairStatus;
    }

    public BlueprintPlanningResult(
            final CanonicalBlueprint canonicalBlueprint,
            final boolean accepted,
            final Map<String, Object> qualitySummary,
            final Map<String, Object> repairStatus) {
        this(
                canonicalBlueprint,
                accepted,
                PlanningDecisionPayloadMapper.fromLegacyQualitySummary(qualitySummary),
                PlanningDecisionPayloadMapper.fromLegacyRepairStatus(repairStatus));
    }

    public Map<String, Object> qualitySummaryPayload() {
        return PlanningDecisionPayloadMapper.toLegacyQualitySummary(qualitySummary);
    }

    public Map<String, Object> repairStatusPayload() {
        return PlanningDecisionPayloadMapper.toLegacyRepairStatus(repairStatus);
    }
}
