package com.arslanca.orchestrai.business.dtos.orchestration;

public record QualityGateThresholdProfile(
        PlanningComplexity effectiveComplexity,
        PlanningShapeEnvelope shapeEnvelope,
        int passingScore,
        int minimumStructuralCompleteness,
        int minimumScopeCoverage,
        int minimumPhaseDepth,
        int minimumRiskCoverage,
        int regenerateScoreGap,
        int maxRepairAttempts) {

    public QualityGateThresholdProfile {
        effectiveComplexity = effectiveComplexity == null ? PlanningComplexity.MODERATE : effectiveComplexity;
        shapeEnvelope = shapeEnvelope == null ? PlanningShapeEnvelope.unknown() : shapeEnvelope;
    }

    public static QualityGateThresholdProfile forProfile(final DeterministicPlanningProfile profile) {
        final DeterministicPlanningProfile normalizedProfile = profile == null
                ? DeterministicPlanningProfile.unknown()
                : profile;
        final PlanningComplexity normalizedComplexity = normalizedProfile.effectiveComplexity();
        return switch (normalizedComplexity) {
            case SIMPLE -> new QualityGateThresholdProfile(normalizedComplexity, normalizedProfile.shapeEnvelope(), 60, 18, 10, 12, 12, 15, 2);
            case MODERATE -> new QualityGateThresholdProfile(normalizedComplexity, normalizedProfile.shapeEnvelope(), 70, 18, 10, 12, 12, 15, 2);
            case COMPLEX -> new QualityGateThresholdProfile(normalizedComplexity, normalizedProfile.shapeEnvelope(), 80, 18, 10, 12, 12, 15, 2);
            case ENTERPRISE -> new QualityGateThresholdProfile(normalizedComplexity, normalizedProfile.shapeEnvelope(), 85, 18, 10, 12, 12, 15, 2);
        };
    }

    public static QualityGateThresholdProfile forComplexity(final PlanningComplexity complexity) {
        final PlanningComplexity normalizedComplexity = complexity == null ? PlanningComplexity.MODERATE : complexity;
        return forProfile(new DeterministicPlanningProfile(
                normalizedComplexity,
                java.util.List.of(),
                PlanningShapeEnvelope.unknown(),
                java.util.List.of()));
    }
}
