package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record PlanningQualitySummary(
        PlanningComplexity complexity,
        List<String> complexityMarkers,
        CapabilityRisk capabilityRisk,
        boolean previewRequired,
        boolean previewRecommended,
        PlannerModelRef plannerModelRef,
        DegradeStrategy degradeStrategy,
        boolean docsOnlyFallback,
        int qualityScore,
        int passingScore,
        QualityVerdict qualityVerdict,
        boolean qualityPassed,
        Map<QualityScoreCategory, Integer> categoryScores,
        PlanningComplexity effectiveComplexity,
        PlanningShapeAssessment shapeAssessment,
        List<PlanningQualityFinding> deterministicFindings,
        List<String> findings,
        PolicyVersionFingerprint policyVersionFingerprint) {

    public PlanningQualitySummary {
        complexity = complexity == null ? PlanningComplexity.MODERATE : complexity;
        complexityMarkers = complexityMarkers == null ? List.of() : List.copyOf(complexityMarkers);
        capabilityRisk = capabilityRisk == null ? CapabilityRisk.UNKNOWN : capabilityRisk;
        plannerModelRef = plannerModelRef == null ? PlannerModelRef.unknown() : plannerModelRef;
        degradeStrategy = degradeStrategy == null ? DegradeStrategy.NONE : degradeStrategy;
        qualityVerdict = qualityVerdict == null ? QualityVerdict.REJECT : qualityVerdict;
        categoryScores = copyCategoryScores(categoryScores);
        effectiveComplexity = effectiveComplexity == null ? complexity : effectiveComplexity;
        shapeAssessment = shapeAssessment == null ? PlanningShapeAssessment.unknown() : shapeAssessment;
        deterministicFindings = deterministicFindings == null ? List.of() : List.copyOf(deterministicFindings);
        findings = findings == null ? List.of() : List.copyOf(findings);
        policyVersionFingerprint = policyVersionFingerprint == null ? PolicyVersionFingerprint.unknown() : policyVersionFingerprint;
    }

    public PlanningQualitySummary(
            final PlanningComplexity complexity,
            final List<String> complexityMarkers,
            final CapabilityRisk capabilityRisk,
            final boolean previewRequired,
            final boolean previewRecommended,
            final PlannerModelRef plannerModelRef,
            final DegradeStrategy degradeStrategy,
            final boolean docsOnlyFallback,
            final int qualityScore,
            final int passingScore,
            final QualityVerdict qualityVerdict,
            final boolean qualityPassed,
            final Map<QualityScoreCategory, Integer> categoryScores,
            final List<String> findings,
            final PolicyVersionFingerprint policyVersionFingerprint) {
        this(
                complexity,
                complexityMarkers,
                capabilityRisk,
                previewRequired,
                previewRecommended,
                plannerModelRef,
                degradeStrategy,
                docsOnlyFallback,
                qualityScore,
                passingScore,
                qualityVerdict,
                qualityPassed,
                categoryScores,
                complexity,
                PlanningShapeAssessment.unknown(),
                List.of(),
                findings,
                policyVersionFingerprint);
    }

    private static Map<QualityScoreCategory, Integer> copyCategoryScores(final Map<QualityScoreCategory, Integer> categoryScores) {
        if (categoryScores == null || categoryScores.isEmpty()) {
            return Map.of();
        }

        final Map<QualityScoreCategory, Integer> copiedScores = new EnumMap<>(QualityScoreCategory.class);
        copiedScores.putAll(categoryScores);
        return Map.copyOf(new LinkedHashMap<>(copiedScores));
    }
}
