package com.arslanca.orchestrai.business.dtos.project;

import java.util.Map;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningDecisionPayloadMapper;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningQualitySummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningRepairOutcome;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ProjectArtifactProvenance(
        String artifactSchemaVersion,
        ResolvedHeavyModelSummary resolvedHeavyModel,
        String selectedStarterKey,
        String selectedStarterTitle,
        String starterSourceKind,
        String starterSourceRef,
        boolean docsOnlyRequested,
        String docsOnlyRequestedReason,
        boolean materializationDegraded,
        String materializationDegradeReason,
        boolean docsOnlyFallback,
        String docsOnlyFallbackReason,
        int artifactFileCount,
        long artifactTotalBytes,
        boolean artifactAvailable,
        String ideTarget,
        String deliveryTarget,
        String deliveryStrategy,
        String deliveryManifestSchemaVersion,
        PlanningQualitySummary planningQualitySummary,
        PlanningRepairOutcome planningRepairOutcome,
        String generationCompletedAt,
        String creditOutcome,
        String terminalStateAt,
        String terminalStateSource) {

    public ProjectArtifactProvenance {
        artifactSchemaVersion = artifactSchemaVersion == null || artifactSchemaVersion.isBlank()
                ? ProjectArtifactData.CURRENT_VERSION
                : artifactSchemaVersion;
        deliveryManifestSchemaVersion = deliveryManifestSchemaVersion == null ? "" : deliveryManifestSchemaVersion.trim();
        planningQualitySummary = planningQualitySummary == null
                ? PlanningDecisionPayloadMapper.fromLegacyQualitySummary(Map.of())
                : planningQualitySummary;
        planningRepairOutcome = planningRepairOutcome == null
                ? PlanningDecisionPayloadMapper.fromLegacyRepairStatus(Map.of())
                : planningRepairOutcome;
    }
}
