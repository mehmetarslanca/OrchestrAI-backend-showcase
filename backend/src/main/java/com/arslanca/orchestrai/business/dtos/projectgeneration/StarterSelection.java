package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

public record StarterSelection(
        UUID preparationId,
        String projectName,
        String ideTarget,
        StarterCandidate recommendedStarter,
        List<StarterCandidate> alternatives,
        boolean docsOnlyFallback,
        String docsOnlyFallbackReason) {

    public StarterSelection {
        preparationId = preparationId == null ? UUID.randomUUID() : preparationId;
        projectName = normalizeText(projectName, "unnamed-project");
        ideTarget = normalizeText(ideTarget, "generic").toLowerCase();
        alternatives = alternatives == null ? List.of() : List.copyOf(new LinkedHashSet<>(alternatives));
        docsOnlyFallbackReason = normalizeText(docsOnlyFallbackReason, "");

        if (docsOnlyFallback) {
            if (recommendedStarter != null) {
                throw new IllegalArgumentException("docsOnlyFallback selections cannot include a recommended starter");
            }
            if (docsOnlyFallbackReason.isBlank()) {
                throw new IllegalArgumentException("docsOnlyFallback selections must include a fallback reason");
            }
        } else if (recommendedStarter == null) {
            throw new IllegalArgumentException("non-fallback selections must include a recommended starter");
        }
    }

    public boolean hasRecommendation() {
        return !docsOnlyFallback && recommendedStarter != null;
    }

    private static String normalizeText(final String value, final String fallbackValue) {
        if (value == null || value.isBlank()) {
            return fallbackValue;
        }
        return value.trim();
    }
}
