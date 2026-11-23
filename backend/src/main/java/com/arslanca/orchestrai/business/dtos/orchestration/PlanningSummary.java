package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanningSummary(
    PlanningComplexity complexity,
    List<String> complexityMarkers,
    String projectShapeSummary,
    PlanningMode selectedPlanningMode,
    CapabilityRisk capabilityRisk,
    boolean previewRecommended,
    boolean previewRequired,
    boolean docsOnlyFallback,
    String starterSummary,
    String resolvedModelKey,
    ModelCapability modelCapability,
    boolean localModel,
    DegradeStrategy degradeStrategy,
    String contractVersion,

    PlanningComplexity effectiveComplexity,
    List<PlanningHardSignal> hardSignals,
    PlanningShapeEnvelope expectedShape
) {
    public static final String CURRENT_CONTRACT_VERSION = "2.1";

    public PlanningSummary {
        complexity = complexity == null ? PlanningComplexity.MODERATE : complexity;
        complexityMarkers = complexityMarkers == null ? List.of() : List.copyOf(complexityMarkers);
        projectShapeSummary = projectShapeSummary == null ? "" : projectShapeSummary.trim();
        selectedPlanningMode = selectedPlanningMode == null ? PlanningMode.SINGLE_PASS : selectedPlanningMode;
        capabilityRisk = capabilityRisk == null ? CapabilityRisk.UNKNOWN : capabilityRisk;
        starterSummary = starterSummary == null ? "" : starterSummary.trim();
        resolvedModelKey = resolvedModelKey == null ? "" : resolvedModelKey.trim();
        modelCapability = modelCapability == null ? ModelCapability.UNKNOWN : modelCapability;
        degradeStrategy = degradeStrategy == null ? DegradeStrategy.NONE : degradeStrategy;
        contractVersion = contractVersion == null || contractVersion.isBlank() ? CURRENT_CONTRACT_VERSION : contractVersion.trim();
        effectiveComplexity = effectiveComplexity == null ? complexity : effectiveComplexity;
        hardSignals = hardSignals == null ? List.of() : List.copyOf(hardSignals);
        expectedShape = expectedShape == null ? PlanningShapeEnvelope.unknown() : expectedShape;
    }

    public PlanningSummary(
            final PlanningComplexity complexity,
            final List<String> complexityMarkers,
            final String projectShapeSummary,
            final PlanningMode selectedPlanningMode,
            final CapabilityRisk capabilityRisk,
            final boolean previewRecommended,
            final boolean previewRequired,
            final boolean docsOnlyFallback,
            final String starterSummary,
            final String resolvedModelKey,
            final ModelCapability modelCapability,
            final boolean localModel,
            final DegradeStrategy degradeStrategy,
            final String contractVersion) {
        this(
                complexity,
                complexityMarkers,
                projectShapeSummary,
                selectedPlanningMode,
                capabilityRisk,
                previewRecommended,
                previewRequired,
                docsOnlyFallback,
                starterSummary,
                resolvedModelKey,
                modelCapability,
                localModel,
                degradeStrategy,
                contractVersion,
                complexity,
                List.of(),
                PlanningShapeEnvelope.unknown()
        );
    }

    public PlanningSummary(
            final PlanningComplexity complexity,
            final List<String> complexityMarkers,
            final String projectShapeSummary,
            final PlanningMode selectedPlanningMode,
            final String capabilityRisk,
            final boolean previewRecommended,
            final boolean previewRequired,
            final boolean docsOnlyFallback,
            final String starterSummary,
            final String resolvedModelKey,
            final ModelCapability modelCapability,
            final boolean localModel,
            final String degradeStrategy,
            final String contractVersion) {
        this(
                complexity,
                complexityMarkers,
                projectShapeSummary,
                selectedPlanningMode,
                CapabilityRisk.fromValue(capabilityRisk),
                previewRecommended,
                previewRequired,
                docsOnlyFallback,
                starterSummary,
                resolvedModelKey,
                modelCapability,
                localModel,
                DegradeStrategy.fromValue(degradeStrategy),
                contractVersion,
                complexity,
                List.of(),
                PlanningShapeEnvelope.unknown());
    }

    public PlannerModelRef plannerModelRef() {
        return new PlannerModelRef(resolvedModelKey, modelCapability, localModel);
    }

    public PolicyVersionFingerprint policyVersionFingerprint() {
        return PolicyVersionFingerprint.from(
                selectedPlanningMode,
                capabilityRisk,
                previewRecommended,
                previewRequired,
                plannerModelRef(),
                degradeStrategy);
    }
}
