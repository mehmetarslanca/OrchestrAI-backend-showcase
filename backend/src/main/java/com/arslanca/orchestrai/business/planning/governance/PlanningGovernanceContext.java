package com.arslanca.orchestrai.business.planning.governance;

import java.util.List;

import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;

public record PlanningGovernanceContext(
        ProjectGenerationPreparationEntity preparation,
        StructuredAnalysis structuredAnalysis,
        DeterministicPlanningProfile planningProfile,
        String selectedStarterKey,
        String ideTarget,
        PlanningModelPolicy planningModelPolicy,
        String previewContractVersion) {

    public PlanningGovernanceContext {
        planningProfile = planningProfile == null ? DeterministicPlanningProfile.unknown() : planningProfile;
        selectedStarterKey = selectedStarterKey == null ? "" : selectedStarterKey.trim();
        ideTarget = normalizeText(ideTarget);
        previewContractVersion = previewContractVersion == null || previewContractVersion.isBlank()
                ? PlanningPreview.CURRENT_CONTRACT_VERSION
                : previewContractVersion.trim();
    }

    public PlanningGovernanceContext(
            final ProjectGenerationPreparationEntity preparation,
            final StructuredAnalysis structuredAnalysis,
            final String selectedStarterKey,
            final PlanningModelPolicy planningModelPolicy,
            final String previewContractVersion) {
        this(
                preparation,
                structuredAnalysis,
                new DeterministicPlanningProfile(
                        structuredAnalysis == null ? PlanningComplexity.MODERATE : structuredAnalysis.planningComplexity(),
                        List.of(),
                        PlanningShapeEnvelope.unknown(),
                        List.of()),
                selectedStarterKey,
                null,
                planningModelPolicy,
                previewContractVersion);
    }

    public PlanningComplexity effectiveComplexity() {
        if (planningProfile != null) {
            return planningProfile.effectiveComplexity();
        }
        return structuredAnalysis == null ? PlanningComplexity.MODERATE : structuredAnalysis.planningComplexity();
    }

    public PlanningShapeEnvelope shapeEnvelope() {
        return planningProfile == null ? PlanningShapeEnvelope.unknown() : planningProfile.shapeEnvelope();
    }

    public String resolvedIdeTarget() {
        final String preparationIdeTarget = preparation == null ? null : normalizeText(preparation.getIdeTarget());
        return preparationIdeTarget == null ? ideTarget : preparationIdeTarget;
    }

    public String preparationContractVersion() {
        final String contractVersion = preparation == null ? null : normalizeText(preparation.getContractVersion());
        return contractVersion == null ? "" : contractVersion;
    }

    private static String normalizeText(final String value) {
        if (value == null) {
            return null;
        }
        final String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }
}
