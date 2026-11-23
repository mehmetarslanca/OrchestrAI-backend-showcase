package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.List;
import java.util.UUID;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary;

public record GenerationPreparationResponse(
        UUID preparationId,
        String projectName,
        String ideTarget,
        StarterCandidate recommendedStarter,
        List<StarterCandidate> alternatives,
        boolean docsOnlyFallback,
        String docsOnlyFallbackReason,
        PlanningSummary planningSummary) {

    public GenerationPreparationResponse(
            UUID preparationId,
            String projectName,
            String ideTarget,
            StarterCandidate recommendedStarter,
            List<StarterCandidate> alternatives,
            boolean docsOnlyFallback,
            String docsOnlyFallbackReason) {
        this(preparationId, projectName, ideTarget, recommendedStarter, alternatives, docsOnlyFallback, docsOnlyFallbackReason, null);
    }

}
