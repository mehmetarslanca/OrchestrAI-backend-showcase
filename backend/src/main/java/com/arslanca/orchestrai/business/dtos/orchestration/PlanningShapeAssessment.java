package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

public record PlanningShapeAssessment(
        PlanningShapeEnvelope expectedShape,
        PlanningActualShape actualShape,
        boolean workstreamCountSatisfied,
        boolean phaseCountSatisfied,
        boolean agentCountSatisfied,
        List<PlanningCoverageFamily> missingCoverageFamilies) {

    public PlanningShapeAssessment {
        expectedShape = expectedShape == null ? PlanningShapeEnvelope.unknown() : expectedShape;
        actualShape = actualShape == null ? PlanningActualShape.empty() : actualShape;
        missingCoverageFamilies = missingCoverageFamilies == null ? List.of() : List.copyOf(missingCoverageFamilies);
    }

    public static PlanningShapeAssessment unknown() {
        return new PlanningShapeAssessment(
                PlanningShapeEnvelope.unknown(),
                PlanningActualShape.empty(),
                true,
                true,
                true,
                List.of());
    }

    public boolean hasShapeDeficit() {
        return !workstreamCountSatisfied
                || !phaseCountSatisfied
                || !agentCountSatisfied
                || !actualShape.workstreamsWithoutPhases().isEmpty();
    }

    public boolean hasCoverageDeficit() {
        return !missingCoverageFamilies.isEmpty();
    }
}
