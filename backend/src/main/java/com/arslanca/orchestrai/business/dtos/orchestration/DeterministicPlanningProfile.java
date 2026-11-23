package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DeterministicPlanningProfile(
        PlanningComplexity effectiveComplexity,
        List<PlanningHardSignal> hardSignals,
        PlanningShapeEnvelope shapeEnvelope,
        List<String> decisionReasons
) {

    public DeterministicPlanningProfile {
        effectiveComplexity = effectiveComplexity == null ? PlanningComplexity.MODERATE : effectiveComplexity;
        hardSignals = hardSignals == null ? List.of() : List.copyOf(hardSignals);
        shapeEnvelope = shapeEnvelope == null ? PlanningShapeEnvelope.unknown() : shapeEnvelope;
        decisionReasons = decisionReasons == null ? List.of() : List.copyOf(decisionReasons);
    }

    public static DeterministicPlanningProfile unknown() {
        return new DeterministicPlanningProfile(
                PlanningComplexity.MODERATE,
                List.of(),
                PlanningShapeEnvelope.unknown(),
                List.of());
    }
}
