package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanningPreview(
        UUID previewId,
        UUID preparationId,
        String selectedStarterKey,
        PlanningMode planningMode,
        PlanningPreviewStatus previewStatus,
        Map<String, Object> qualitySummary,
        Map<String, Object> repairStatus,
        String previewContractVersion,
        BlueprintPlan blueprintPayload) {

    public static final String CURRENT_CONTRACT_VERSION = "3.0";

    public PlanningPreview {
        selectedStarterKey = selectedStarterKey == null ? "" : selectedStarterKey.trim();
        planningMode = planningMode == null ? PlanningMode.SINGLE_PASS : planningMode;
        previewStatus = previewStatus == null ? PlanningPreviewStatus.DRAFT : previewStatus;
        qualitySummary = qualitySummary == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(qualitySummary));
        repairStatus = repairStatus == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(repairStatus));
        previewContractVersion = previewContractVersion == null || previewContractVersion.isBlank()
                ? CURRENT_CONTRACT_VERSION
                : previewContractVersion.trim();
    }
}
