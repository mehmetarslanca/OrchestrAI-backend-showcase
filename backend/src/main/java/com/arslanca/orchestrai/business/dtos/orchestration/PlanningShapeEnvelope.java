package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PlanningShapeEnvelope(
        int minWorkstreamCount,
        int maxWorkstreamCount,
        int minPhaseCount,
        int maxPhaseCount,
        int minAgentCount,
        int maxAgentCount,
        List<PlanningCoverageFamily> requiredCoverageFamilies,
        String outputBand,
        String strongerModelWarningReason
) {

    public PlanningShapeEnvelope {
        minWorkstreamCount = Math.max(0, minWorkstreamCount);
        maxWorkstreamCount = Math.max(minWorkstreamCount, maxWorkstreamCount);
        minPhaseCount = Math.max(0, minPhaseCount);
        maxPhaseCount = Math.max(minPhaseCount, maxPhaseCount);
        minAgentCount = Math.max(0, minAgentCount);
        maxAgentCount = Math.max(minAgentCount, maxAgentCount);
        requiredCoverageFamilies = requiredCoverageFamilies == null ? List.of() : List.copyOf(requiredCoverageFamilies);
        outputBand = outputBand == null || outputBand.isBlank()
                ? defaultOutputBand(minWorkstreamCount, maxWorkstreamCount, minPhaseCount, maxPhaseCount, minAgentCount, maxAgentCount)
                : outputBand.trim();
        strongerModelWarningReason = strongerModelWarningReason == null ? "" : strongerModelWarningReason.trim();
    }

    public static PlanningShapeEnvelope unknown() {
        return new PlanningShapeEnvelope(0, 0, 0, 0, 0, 0, List.of(), "", "");
    }

    private static String defaultOutputBand(
            final int minWorkstreamCount,
            final int maxWorkstreamCount,
            final int minPhaseCount,
            final int maxPhaseCount,
            final int minAgentCount,
            final int maxAgentCount) {
        if (maxWorkstreamCount == 0 && maxPhaseCount == 0 && maxAgentCount == 0) {
            return "";
        }
        return "%d-%d workstreams | %d-%d phases | %d-%d agents".formatted(
                minWorkstreamCount,
                maxWorkstreamCount,
                minPhaseCount,
                maxPhaseCount,
                minAgentCount,
                maxAgentCount);
    }
}
