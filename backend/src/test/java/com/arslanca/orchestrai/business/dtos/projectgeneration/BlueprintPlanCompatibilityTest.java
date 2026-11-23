package com.arslanca.orchestrai.business.dtos.projectgeneration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class BlueprintPlanCompatibilityTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldDeserializeLegacyBlueprintPayload() throws Exception {
        final String legacyJson = """
                {
                  "projectName": "legacy-project",
                  "architectureOverview": "overview",
                  "phases": [
                    {
                      "order": 1,
                      "title": "Phase 1",
                      "description": "Legacy phase",
                      "deliverables": ["docs"],
                      "constraints": ["keep it safe"]
                    }
                  ],
                  "agents": [],
                  "ideRules": "rules",
                  "starterNotes": "notes"
                }
                """;

        final BlueprintPlan blueprintPlan = objectMapper.readValue(legacyJson, BlueprintPlan.class);

        assertThat(blueprintPlan.projectName()).isEqualTo("legacy-project");
        assertThat(blueprintPlan.executiveSummary()).isEmpty();
        assertThat(blueprintPlan.workstreams()).isEmpty();
        assertThat(blueprintPlan.assumptions()).isEmpty();
        assertThat(blueprintPlan.unknowns()).isEmpty();
        assertThat(blueprintPlan.previewManifest()).isEmpty();
        assertThat(blueprintPlan.contractVersion()).isEqualTo(BlueprintPlan.CURRENT_CONTRACT_VERSION);
        assertThat(blueprintPlan.phases()).hasSize(1);
        assertThat(blueprintPlan.phases().getFirst().description()).isEqualTo("Legacy phase");
    }

    @Test
    void shouldFlattenWorkstreamPhasesWhenLegacyPhasesMissing() {
        final BlueprintPlan.PhaseDefinition phase = new BlueprintPlan.PhaseDefinition(
                1,
                "Foundation",
                "Ship the foundation",
                "Needed to set the system context",
                List.of("auth"),
                List.of("mobile"),
                List.of("approved stack"),
                List.of("docs", "api"),
                List.of("jwt auth"),
                List.of("public api"),
                "adds user tables",
                List.of("rate limit endpoints"),
                "latency metrics",
                "unit and integration tests",
                List.of("tests pass"),
                List.of("delivery drift"),
                "handoff to backend engineer",
                "1.0",
                Map.of("phase", "foundation"),
                "",
                List.of());

        final BlueprintPlan blueprintPlan = new BlueprintPlan(
                "rich-project",
                "Executive summary",
                "Architecture overview",
                "System context",
                List.of("Choose modular monolith"),
                List.of(new BlueprintPlan.WorkstreamDefinition(
                        "Core Backend",
                        "Create the API foundation",
                        "Needed to ship the backend path",
                        List.of("auth", "api"),
                        List.of("mobile"),
                        List.of("starter approved"),
                        List.of("backend-engineer"),
                        List.of(phase))),
                List.of("availability"),
                List.of("security"),
                List.of("requirement packet"),
                List.of("Starter is approved"),
                List.of("Analytics stack undecided"),
                Map.of("workstreamCount", 1),
                BlueprintPlan.CURRENT_CONTRACT_VERSION,
                List.of(),
                List.of(),
                "rules",
                "starter notes");

        assertThat(blueprintPlan.workstreams()).hasSize(1);
        assertThat(blueprintPlan.phases()).hasSize(1);
        assertThat(blueprintPlan.phases().getFirst().title()).isEqualTo("Foundation");
        assertThat(blueprintPlan.phases().getFirst().description()).isEqualTo("Ship the foundation");
        assertThat(blueprintPlan.phases().getFirst().previewManifest()).containsEntry("phase", "foundation");
        assertThat(blueprintPlan.assumptions()).containsExactly("Starter is approved");
        assertThat(blueprintPlan.unknowns()).containsExactly("Analytics stack undecided");
        assertThat(blueprintPlan.previewManifest()).containsEntry("workstreamCount", 1);
    }
}
