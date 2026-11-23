package com.arslanca.orchestrai.business.delivery.manifest;

import java.util.List;

public record ManifestWorkstreamEntry(
        String title,
        String goal,
        String whyItExists,
        List<String> includedScope,
        List<String> excludedScope,
        List<String> dependencies,
        List<String> suggestedAgents,
        List<String> phaseDocumentIds) {

    public ManifestWorkstreamEntry {
        title = title == null ? "" : title.trim();
        goal = goal == null ? "" : goal.trim();
        whyItExists = whyItExists == null ? "" : whyItExists.trim();
        includedScope = includedScope == null ? List.of() : List.copyOf(includedScope);
        excludedScope = excludedScope == null ? List.of() : List.copyOf(excludedScope);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        suggestedAgents = suggestedAgents == null ? List.of() : List.copyOf(suggestedAgents);
        phaseDocumentIds = phaseDocumentIds == null ? List.of() : List.copyOf(phaseDocumentIds);
    }
}
