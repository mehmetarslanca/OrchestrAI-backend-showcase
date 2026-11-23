package com.arslanca.orchestrai.business.dtos.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class ProjectArtifactCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldReadLegacyArtifactDataWithoutManifest() throws Exception {
        final String payload = """
                {
                  "version": "v1",
                  "tree": [
                    {
                      "path": "docs/architect.md",
                      "content": "architecture"
                    }
                  ]
                }
                """;

        final ProjectArtifactData artifactData = objectMapper.readValue(payload, ProjectArtifactData.class);

        assertThat(artifactData.version()).isEqualTo("v1");
        assertThat(artifactData.tree()).singleElement().extracting(file -> file.path()).isEqualTo("docs/architect.md");
        assertThat(artifactData.manifest()).isNotNull();
        assertThat(artifactData.manifest().inventory()).isEmpty();
    }

    @Test
    void shouldReadLegacyArtifactProvenanceWithoutPlanningFields() throws Exception {
        final String payload = """
                {
                  "artifactSchemaVersion": "v1",
                  "artifactFileCount": 1,
                  "artifactTotalBytes": 42,
                  "artifactAvailable": true,
                  "ideTarget": "cursor",
                  "deliveryTarget": "cursor",
                  "deliveryStrategy": "cursor",
                  "generationCompletedAt": "2026-04-13T00:00:00Z",
                  "creditOutcome": "CAPTURED",
                  "terminalStateAt": "2026-04-13T00:00:00Z",
                  "terminalStateSource": "FINALIZATION"
                }
                """;

        final ProjectArtifactProvenance provenance = objectMapper.readValue(payload, ProjectArtifactProvenance.class);

        assertThat(provenance.artifactSchemaVersion()).isEqualTo("v1");
        assertThat(provenance.planningQualitySummary()).isNotNull();
        assertThat(provenance.planningRepairOutcome()).isNotNull();
        assertThat(provenance.deliveryManifestSchemaVersion()).isBlank();
    }
}
