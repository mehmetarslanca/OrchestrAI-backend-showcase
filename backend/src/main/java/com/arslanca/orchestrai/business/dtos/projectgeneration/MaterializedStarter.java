package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.List;
import java.util.Objects;

public record MaterializedStarter(
        String starterKey,
        StarterSourceKind sourceKind,
        String sourceRef,
        List<MaterializedFileNode> tree,
        MaterializationMetadata metadata,
        boolean degraded) {

    public MaterializedStarter {
        starterKey = normalizeToken(starterKey, "starterKey");
        sourceKind = Objects.requireNonNull(sourceKind, "sourceKind cannot be null");
        sourceRef = normalizeToken(sourceRef, "sourceRef");
        tree = tree == null ? List.of() : List.copyOf(tree);
        metadata = metadata == null ? new MaterializationMetadata(tree.size(), 0L, 0L, List.of(), degraded, "") : metadata;
        degraded = degraded || metadata.degraded();
    }

    public static MaterializedStarter degraded(
            final String starterKey,
            final StarterSourceKind sourceKind,
            final String sourceRef,
            final long durationMs,
            final String warning) {
        return new MaterializedStarter(
                starterKey,
                sourceKind,
                sourceRef,
                List.of(),
                new MaterializationMetadata(
                        0,
                        0L,
                        durationMs,
                        warning == null || warning.isBlank() ? List.of() : List.of(warning.trim()),
                        true,
                        warning),
                true);
    }

    public boolean hasFiles() {
        return !tree.isEmpty();
    }

    private static String normalizeToken(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank");
        }
        return value.trim().toLowerCase();
    }
}
