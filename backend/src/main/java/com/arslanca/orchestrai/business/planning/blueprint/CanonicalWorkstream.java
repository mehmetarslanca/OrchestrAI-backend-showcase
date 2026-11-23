package com.arslanca.orchestrai.business.planning.blueprint;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalWorkstream(
        String title,
        String goal,
        String whyItExists,
        List<String> includedScope,
        List<String> excludedScope,
        List<String> dependencies,
        List<String> suggestedAgents,
        List<CanonicalPhase> phases) {
    public CanonicalWorkstream {
        title = normalizeText(title);
        goal = normalizeText(goal);
        whyItExists = normalizeText(whyItExists);
        includedScope = includedScope == null ? List.of() : List.copyOf(includedScope);
        excludedScope = excludedScope == null ? List.of() : List.copyOf(excludedScope);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        suggestedAgents = suggestedAgents == null ? List.of() : List.copyOf(suggestedAgents);
        phases = phases == null ? List.of() : List.copyOf(phases);
    }

    private static String normalizeText(final String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
