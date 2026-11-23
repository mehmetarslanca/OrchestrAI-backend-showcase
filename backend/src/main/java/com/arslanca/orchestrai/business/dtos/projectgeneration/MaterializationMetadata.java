package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.List;

public record MaterializationMetadata(
        int fileCount,
        long totalBytes,
        long durationMs,
        List<String> warnings,
        boolean degraded,
        String degradeReason) {

    public MaterializationMetadata {
        if (fileCount < 0) {
            throw new IllegalArgumentException("fileCount cannot be negative");
        }
        if (totalBytes < 0) {
            throw new IllegalArgumentException("totalBytes cannot be negative");
        }
        if (durationMs < 0) {
            throw new IllegalArgumentException("durationMs cannot be negative");
        }
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        degradeReason = degradeReason == null ? "" : degradeReason.trim();
        if (!degraded && !degradeReason.isBlank()) {
            degraded = true;
        }
    }
}
