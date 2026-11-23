package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

public record PlanningActualShape(
        int workstreamCount,
        int phaseCount,
        int agentCount,
        List<PlanningCoverageFamily> coveredFamilies,
        List<String> workstreamsWithoutPhases) {

    public PlanningActualShape {
        workstreamCount = Math.max(0, workstreamCount);
        phaseCount = Math.max(0, phaseCount);
        agentCount = Math.max(0, agentCount);
        coveredFamilies = coveredFamilies == null ? List.of() : List.copyOf(coveredFamilies);
        workstreamsWithoutPhases = workstreamsWithoutPhases == null ? List.of() : List.copyOf(workstreamsWithoutPhases);
    }

    public static PlanningActualShape empty() {
        return new PlanningActualShape(0, 0, 0, List.of(), List.of());
    }
}
