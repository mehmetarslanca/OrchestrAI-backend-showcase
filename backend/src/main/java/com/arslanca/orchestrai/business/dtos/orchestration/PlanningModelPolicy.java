package com.arslanca.orchestrai.business.dtos.orchestration;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;

public record PlanningModelPolicy(
        PlanningMode selectedPlanningMode,
        CapabilityRisk capabilityRisk,
        boolean previewRecommended,
        boolean previewRequired,
        PlannerModelRef plannerModelRef,
        DegradeStrategy degradeStrategy,
        PolicyVersionFingerprint policyVersionFingerprint) {

    public PlanningModelPolicy {
        selectedPlanningMode = selectedPlanningMode == null ? PlanningMode.SINGLE_PASS : selectedPlanningMode;
        capabilityRisk = capabilityRisk == null ? CapabilityRisk.UNKNOWN : capabilityRisk;
        plannerModelRef = plannerModelRef == null ? PlannerModelRef.unknown() : plannerModelRef;
        degradeStrategy = degradeStrategy == null ? DegradeStrategy.NONE : degradeStrategy;
        policyVersionFingerprint = policyVersionFingerprint == null
                ? PolicyVersionFingerprint.from(
                        selectedPlanningMode,
                        capabilityRisk,
                        previewRecommended,
                        previewRequired,
                        plannerModelRef,
                        degradeStrategy)
                : policyVersionFingerprint;
    }

    public PlanningModelPolicy(
            final PlanningMode selectedPlanningMode,
            final CapabilityRisk capabilityRisk,
            final boolean previewRecommended,
            final boolean previewRequired,
            final PlannerModelRef plannerModelRef,
            final DegradeStrategy degradeStrategy) {
        this(
                selectedPlanningMode,
                capabilityRisk,
                previewRecommended,
                previewRequired,
                plannerModelRef,
                degradeStrategy,
                null);
    }

    public PlanningModelPolicy(
            final PlanningMode selectedPlanningMode,
            final String capabilityRisk,
            final boolean previewRecommended,
            final boolean previewRequired,
            final String resolvedModelKey,
            final ModelCapability modelCapability,
            final boolean localModel,
            final String degradeStrategy) {
        this(
                selectedPlanningMode,
                CapabilityRisk.fromValue(capabilityRisk),
                previewRecommended,
                previewRequired,
                new PlannerModelRef(resolvedModelKey, modelCapability, localModel),
                DegradeStrategy.fromValue(degradeStrategy),
                null);
    }

    public String resolvedModelKey() {
        return plannerModelRef.resolvedModelKey();
    }

    public ModelCapability modelCapability() {
        return plannerModelRef.modelCapability();
    }

    public boolean localModel() {
        return plannerModelRef.localModel();
    }

    public boolean blocksPreviewGeneration() {
        return degradeStrategy.blocksPreviewGeneration();
    }
}
