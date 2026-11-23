package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.LinkedHashSet;
import java.util.List;

public record StarterCandidate(
        String key,
        String title,
        String family,
        StarterSourceKind sourceKind,
        String sourceRef,
        List<String> stackTags,
        String reason) {

    public StarterCandidate {
        key = normalizeToken(key);
        title = normalizeText(title);
        family = normalizeToken(family);
        sourceKind = sourceKind == null ? StarterSourceKind.REPO_PACK : sourceKind;
        sourceRef = normalizeToken(sourceRef);
        stackTags = stackTags == null ? List.of() : List.copyOf(new LinkedHashSet<>(stackTags));
        reason = normalizeText(reason);
    }

    private static String normalizeToken(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim().toLowerCase();
    }

    private static String normalizeText(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim();
    }
}
