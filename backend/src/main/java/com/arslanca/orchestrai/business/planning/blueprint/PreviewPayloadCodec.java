package com.arslanca.orchestrai.business.planning.blueprint;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class PreviewPayloadCodec {
    public static final String CURRENT_PAYLOAD_VERSION = BlueprintPayloadEnvelope.CURRENT_PAYLOAD_VERSION;

    private final ObjectMapper objectMapper;
    private final BlueprintPlanCompatibilityAdapter adapter;

    public PreviewPayloadCodec(final ObjectMapper objectMapper, final BlueprintPlanCompatibilityAdapter adapter) {
        this.objectMapper = objectMapper;
        this.adapter = adapter;
    }

    public CanonicalBlueprint decodeCanonical(final Map<String, Object> rawPayload) {
        if (rawPayload == null || rawPayload.isEmpty()) {
            return null;
        }

        if (isVersionedPayload(rawPayload)) {
            return decodeVersionedPayload(rawPayload);
        }

        try {
            final BlueprintPlan legacyPlan = objectMapper.convertValue(rawPayload, BlueprintPlan.class);
            return adapter.toCanonicalBlueprint(legacyPlan);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to parse legacy BlueprintPlan payload", exception);
        }
    }

    public BlueprintPlan decodeBlueprintPlan(final Map<String, Object> rawPayload) {
        final CanonicalBlueprint canonicalBlueprint = decodeCanonical(rawPayload);
        return canonicalBlueprint == null ? null : adapter.toBlueprintPlan(canonicalBlueprint);
    }

    public Map<String, Object> encode(final CanonicalBlueprint canonical) {
        if (canonical == null) {
            return Map.of();
        }
        return objectMapper.convertValue(
                new BlueprintPayloadEnvelope(CURRENT_PAYLOAD_VERSION, canonical),
                new TypeReference<Map<String, Object>>() {
                });
    }

    private boolean isVersionedPayload(final Map<String, Object> rawPayload) {
        return rawPayload.containsKey("payloadVersion") || rawPayload.containsKey("schemaVersion");
    }

    private CanonicalBlueprint decodeVersionedPayload(final Map<String, Object> rawPayload) {
        final Object versionValue = rawPayload.containsKey("payloadVersion")
                ? rawPayload.get("payloadVersion")
                : rawPayload.get("schemaVersion");
        final String payloadVersion = versionValue == null ? "" : versionValue.toString().trim();
        if (!CURRENT_PAYLOAD_VERSION.equals(payloadVersion)) {
            throw new IllegalStateException("Unsupported preview payload version: " + payloadVersion);
        }

        final Object canonicalPayload = rawPayload.get("canonicalBlueprint");
        if (!(canonicalPayload instanceof Map<?, ?> canonicalMap)) {
            throw new IllegalStateException("Versioned preview payload is missing canonicalBlueprint content");
        }

        try {
            @SuppressWarnings("unchecked")
            final Map<String, Object> payloadMap = (Map<String, Object>) canonicalMap;
            return objectMapper.convertValue(payloadMap, CanonicalBlueprint.class);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException("Failed to parse canonical preview payload", exception);
        }
    }
}
