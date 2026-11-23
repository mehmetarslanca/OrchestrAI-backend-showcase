package com.arslanca.orchestrai.business.planning.governance;

import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PolicyVersionFingerprint;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;

public record PlanningGovernanceSnapshot(
        String schemaVersion,
        PlanningComplexity complexity,
        PlanningComplexity effectiveComplexity,
        PlanningShapeEnvelope shapeEnvelope,
        PlanningMode planningMode,
        PlannerModelRef plannerModelRef,
        PolicyVersionFingerprint planningPolicyFingerprint,
        PolicyVersionFingerprint governancePolicyFingerprint,
        String selectedStarterKey,
        String ideTarget,
        DegradeStrategy degradeStrategy,
        String preparationContractVersion,
        String previewContractVersion,
        PlanningBudgetDecision budgetDecision,
        PreviewBillingDecision billingDecision,
        PreviewCacheDecision cacheDecision,
        PlanningRolloutDecision rolloutDecision) {

    public static final String CURRENT_SCHEMA_VERSION = "1.0";

    public PlanningGovernanceSnapshot {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? CURRENT_SCHEMA_VERSION : schemaVersion.trim();
        complexity = complexity == null ? PlanningComplexity.MODERATE : complexity;
        effectiveComplexity = effectiveComplexity == null ? complexity : effectiveComplexity;
        shapeEnvelope = shapeEnvelope == null ? PlanningShapeEnvelope.unknown() : shapeEnvelope;
        planningMode = planningMode == null ? PlanningMode.SINGLE_PASS : planningMode;
        plannerModelRef = plannerModelRef == null ? PlannerModelRef.unknown() : plannerModelRef;
        planningPolicyFingerprint = planningPolicyFingerprint == null
                ? PolicyVersionFingerprint.unknown()
                : planningPolicyFingerprint;
        governancePolicyFingerprint = governancePolicyFingerprint == null
                ? PolicyVersionFingerprint.unknown()
                : governancePolicyFingerprint;
        selectedStarterKey = selectedStarterKey == null ? "" : selectedStarterKey.trim();
        ideTarget = ideTarget == null ? "" : ideTarget.trim();
        degradeStrategy = degradeStrategy == null ? DegradeStrategy.NONE : degradeStrategy;
        preparationContractVersion = preparationContractVersion == null ? "" : preparationContractVersion.trim();
        previewContractVersion = previewContractVersion == null || previewContractVersion.isBlank()
                ? PlanningPreview.CURRENT_CONTRACT_VERSION
                : previewContractVersion.trim();
        budgetDecision = budgetDecision == null
                ? new PlanningBudgetDecision(true, "moderate_partial", 1, 0, true, true, "")
                : budgetDecision;
        billingDecision = billingDecision == null
                ? new PreviewBillingDecision(null, null, null, null, false)
                : billingDecision;
        cacheDecision = cacheDecision == null
                ? new PreviewCacheDecision("", "unknown", "unknown", 0L, 0L, false)
                : cacheDecision;
        rolloutDecision = rolloutDecision == null
                ? new PlanningRolloutDecision(PlanningRolloutMode.OFF, false, false, false, "")
                : rolloutDecision;
    }

    public PlanningGovernanceSnapshot(
            final String schemaVersion,
            final PlanningComplexity complexity,
            final PlanningMode planningMode,
            final PlannerModelRef plannerModelRef,
            final PolicyVersionFingerprint planningPolicyFingerprint,
            final PolicyVersionFingerprint governancePolicyFingerprint,
            final PlanningBudgetDecision budgetDecision,
            final PreviewBillingDecision billingDecision,
            final PreviewCacheDecision cacheDecision,
            final PlanningRolloutDecision rolloutDecision) {
        this(
                schemaVersion,
                complexity,
                complexity,
                PlanningShapeEnvelope.unknown(),
                planningMode,
                plannerModelRef,
                planningPolicyFingerprint,
                governancePolicyFingerprint,
                "",
                "",
                DegradeStrategy.NONE,
                "",
                PlanningPreview.CURRENT_CONTRACT_VERSION,
                budgetDecision,
                billingDecision,
                cacheDecision,
                rolloutDecision);
    }
}
