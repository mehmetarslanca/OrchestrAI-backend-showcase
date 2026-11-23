package com.arslanca.orchestrai.business.dtos.orchestration;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class StructuredAnalysisCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeLegacyStructuredAnalysisPayload() throws Exception {
        final String legacyJson = """
                {
                  "projectName": "legacy-project",
                  "projectType": "api",
                  "techStack": ["spring-boot"],
                  "features": ["auth"],
                  "complexity": "moderate",
                  "isMalicious": false,
                  "maliciousReason": null,
                  "summary": "Legacy planning payload"
                }
                """;

        final StructuredAnalysis analysis = objectMapper.readValue(legacyJson, StructuredAnalysis.class);

        assertThat(analysis.projectName()).isEqualTo("legacy-project");
        assertThat(analysis.actors()).isEmpty();
        assertThat(analysis.complexityMarkers()).isEmpty();
        assertThat(analysis.contractVersion()).isEqualTo(StructuredAnalysis.CURRENT_CONTRACT_VERSION);
        assertThat(analysis.backendNeededResolved()).isTrue();
    }

    @Test
    void shouldResolvePlanningFlagsFromRichPayload() {
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "rich-project",
                "web app",
                java.util.List.of("nextjs"),
                java.util.List.of("background processing"),
                "enterprise",
                false,
                null,
                "Rich payload",
                java.util.List.of("admin"),
                java.util.List.of("sell subscriptions"),
                java.util.List.of("sign up"),
                java.util.List.of("web"),
                null,
                null,
                null,
                "cloud",
                "single-tenant",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                "scale",
                "latency",
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of("worker", "async"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);

        assertThat(analysis.planningComplexity()).isEqualTo(PlanningComplexity.ENTERPRISE);
        assertThat(analysis.frontendNeededResolved()).isTrue();
        assertThat(analysis.backendNeededResolved()).isFalse();
        assertThat(analysis.workerNeededResolved()).isTrue();
    }
}
