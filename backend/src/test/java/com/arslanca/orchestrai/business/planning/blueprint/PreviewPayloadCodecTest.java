package com.arslanca.orchestrai.business.planning.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

class PreviewPayloadCodecTest {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final BlueprintPlanCompatibilityAdapter adapter = new BlueprintPlanCompatibilityAdapter();
    private final PreviewPayloadCodec codec = new PreviewPayloadCodec(objectMapper, adapter);

    @Test
    void shouldEncodeAndDecodeVersionedCanonicalPayload() {
        final CanonicalBlueprint canonicalBlueprint = new CanonicalBlueprint(
                "Acme Platform",
                "Executive summary",
                "Architecture overview",
                "System context",
                List.of("Use modular monolith"),
                List.of(),
                List.of("availability"),
                List.of("security"),
                List.of("tenant"),
                List.of("Starter approved"),
                List.of("Analytics undecided"),
                List.of(new CanonicalPhase(
                        1,
                        "Foundation",
                        "Establish architecture",
                        "Needed before delivery",
                        List.of("api"),
                        List.of(),
                        List.of(),
                        List.of("docs"),
                        List.of(),
                        List.of(),
                        "adds tables",
                        List.of("jwt validation"),
                        "latency metrics",
                        "unit tests",
                        List.of("tests pass"),
                        List.of("scope drift"),
                        "handoff")),
                List.of(new CanonicalAgent("backend-engineer", "prompt", List.of("ship backend"))),
                "rules",
                "starter notes",
                CanonicalBlueprint.CURRENT_CONTRACT_VERSION);

        final Map<String, Object> encodedPayload = codec.encode(canonicalBlueprint);

        assertThat(encodedPayload).containsEntry("payloadVersion", PreviewPayloadCodec.CURRENT_PAYLOAD_VERSION);
        assertThat(encodedPayload).containsKey("canonicalBlueprint");

        final CanonicalBlueprint decodedCanonicalBlueprint = codec.decodeCanonical(encodedPayload);
        final BlueprintPlan decodedBoundaryBlueprint = codec.decodeBlueprintPlan(encodedPayload);

        assertThat(decodedCanonicalBlueprint).isEqualTo(canonicalBlueprint);
        assertThat(decodedBoundaryBlueprint.projectName()).isEqualTo("Acme Platform");
        assertThat(decodedBoundaryBlueprint.previewManifest()).containsEntry("phaseCount", 1);
    }

    @Test
    void shouldReadLegacyRawBlueprintPlanPayload() {
        final BlueprintPlan legacyBlueprintPlan = new BlueprintPlan(
                "legacy-project",
                "overview",
                List.of(),
                List.of(),
                "rules",
                "notes");

        final Map<String, Object> legacyPayload = objectMapper.convertValue(legacyBlueprintPlan, MAP_TYPE);

        final CanonicalBlueprint canonicalBlueprint = codec.decodeCanonical(legacyPayload);

        assertThat(canonicalBlueprint.projectName()).isEqualTo("legacy-project");
        assertThat(canonicalBlueprint.contractVersion()).isEqualTo(CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
    }

    @Test
    void shouldHandleNullOrEmptyPayload() {
        assertThat(codec.decodeCanonical(null)).isNull();
        assertThat(codec.decodeCanonical(Map.of())).isNull();
        assertThat(codec.decodeBlueprintPlan(null)).isNull();
    }

    @Test
    void shouldRejectUnsupportedPayloadVersion() {
        assertThatThrownBy(() -> codec.decodeCanonical(Map.of(
                "payloadVersion", "9.0",
                "canonicalBlueprint", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unsupported preview payload version: 9.0");
    }

    @Test
    void shouldAcceptSchemaVersionKeyForBackwardsCompatibility() {
        final Map<String, Object> payload = Map.of(
                "schemaVersion", PreviewPayloadCodec.CURRENT_PAYLOAD_VERSION,
                "canonicalBlueprint", Map.of(
                        "projectName", "schema-version-project",
                        "overview", "schema-version-overview",
                        "contractVersion", "1.0",
                        "phases", List.of(),
                        "agents", List.of()
                )
        );

        final CanonicalBlueprint canonicalBlueprint = codec.decodeCanonical(payload);
        assertThat(canonicalBlueprint).isNotNull();
        assertThat(canonicalBlueprint.projectName()).isEqualTo("schema-version-project");
    }

    @Test
    void shouldRejectVersionedPayloadMissingCanonicalBlueprint() {
        assertThatThrownBy(() -> codec.decodeCanonical(Map.of(
                "payloadVersion", PreviewPayloadCodec.CURRENT_PAYLOAD_VERSION)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("missing canonicalBlueprint content");
    }

    @Test
    void shouldRejectLegacyPayloadWhenParsingFails() {
        // Provide invalid structure for legacy BlueprintPlan to force conversion error
        final Map<String, Object> malformedLegacyPayload = Map.of(
                "projectName", Map.of("should", "be a string")
        );

        assertThatThrownBy(() -> codec.decodeCanonical(malformedLegacyPayload))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse legacy BlueprintPlan payload");
    }

    @Test
    void shouldHandleNullEncoding() {
        assertThat(codec.encode(null)).isEmpty();
    }
}
