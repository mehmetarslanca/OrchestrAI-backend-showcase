package com.arslanca.orchestrai.business.runtime;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public record GenerationUsageContext(
        UUID userId,
        UUID projectId,
        UUID preparationId,
        UUID previewId,
        String creditReservationReference,
        String correlationId) {

    public static GenerationUsageContext empty() {
        return new GenerationUsageContext(null, null, null, null, null, null);
    }

    public boolean captureEnabled() {
        return userId != null;
    }

    public Map<String, Object> baseMetadata() {
        final Map<String, Object> metadata = new LinkedHashMap<>();
        putIfPresent(metadata, "projectId", projectId);
        putIfPresent(metadata, "preparationId", preparationId);
        putIfPresent(metadata, "previewId", previewId);
        putIfPresent(metadata, "creditReservationReference", normalize(creditReservationReference));
        putIfPresent(metadata, "correlationId", normalize(correlationId));
        return metadata.isEmpty() ? Map.of() : Map.copyOf(metadata);
    }

    private static void putIfPresent(final Map<String, Object> metadata, final String key, final Object value) {
        if (key != null && value != null) {
            metadata.put(key, value instanceof String text ? normalize(text) : value);
        }
    }

    private static String normalize(final String value) {
        if (value == null) {
            return null;
        }
        final String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
