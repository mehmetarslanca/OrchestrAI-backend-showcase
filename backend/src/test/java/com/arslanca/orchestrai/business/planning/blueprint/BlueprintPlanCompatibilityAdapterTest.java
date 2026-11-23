package com.arslanca.orchestrai.business.planning.blueprint;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;

class BlueprintPlanCompatibilityAdapterTest {

    private final BlueprintPlanCompatibilityAdapter adapter = new BlueprintPlanCompatibilityAdapter();

    @Test
    void shouldBuildLegacyBoundaryFromCanonicalBlueprint() {
        final CanonicalBlueprint canonicalBlueprint = new CanonicalBlueprint(
                "Acme Platform",
                "Executive summary",
                "Architecture overview",
                "System context",
                List.of("Use modular monolith"),
                List.of(new CanonicalWorkstream(
                        "Backend Core",
                        "Ship the backend",
                        "Needed for delivery",
                        List.of("api", "auth"),
                        List.of("mobile"),
                        List.of("approved starter"),
                        List.of("backend-engineer"),
                        List.of(new CanonicalPhase(
                                1,
                                "Foundation",
                                "Establish the backend contracts",
                                "Needed before delivery work starts",
                                List.of("auth"),
                                List.of("mobile"),
                                List.of("starter approved"),
                                List.of("api module"),
                                List.of("REST API"),
                                List.of("public api"),
                                "creates user tables",
                                List.of("jwt validation"),
                                "trace request latency",
                                "unit and integration coverage",
                                List.of("tests pass"),
                                List.of("scope drift"),
                                "handoff to backend engineer")))),
                List.of("availability"),
                List.of("security"),
                List.of("tenant"),
                List.of("Starter approved"),
                List.of("Analytics undecided"),
                List.of(),
                List.of(new CanonicalAgent("backend-engineer", "prompt", List.of("ship backend"))),
                "rules",
                "starter notes",
                CanonicalBlueprint.CURRENT_CONTRACT_VERSION);

        final BlueprintPlan blueprintPlan = adapter.toBlueprintPlan(canonicalBlueprint);

        assertThat(blueprintPlan.contractVersion()).isEqualTo(BlueprintPlan.CURRENT_CONTRACT_VERSION);
        assertThat(blueprintPlan.previewManifest()).containsEntry("workstreamCount", 1);
        assertThat(blueprintPlan.previewManifest()).containsEntry("phaseCount", 1);
        assertThat(blueprintPlan.previewManifest()).containsEntry("agentCount", 1);
        assertThat(blueprintPlan.previewManifest()).containsKey("documentInventory");
        assertThat(blueprintPlan.previewManifest()).containsKey("sectionAvailability");
        assertThat(blueprintPlan.previewManifest()).containsEntry("parityScope", "logical_document_inventory");
        assertThat(blueprintPlan.previewManifest()).containsKey("workstreamSummaries");
        assertThat(blueprintPlan.previewManifest()).containsKey("phaseSummaries");
        assertThat(blueprintPlan.previewManifest()).containsKey("agentSummaries");
        assertThat(blueprintPlan.previewManifest()).containsKey("deliveryWarnings");
        assertThat(blueprintPlan.phases()).hasSize(1);
        assertThat(blueprintPlan.phases().getFirst().description()).isEqualTo("Establish the backend contracts");
        assertThat(blueprintPlan.phases().getFirst().contractVersion()).isEqualTo("1.0");
    }

    @Test
    void shouldPromoteLegacyPhaseDescriptionIntoCanonicalObjective() {
        final BlueprintPlan legacyBlueprintPlan = new BlueprintPlan(
                "legacy-project",
                "overview",
                List.of(new BlueprintPlan.PhaseDefinition(
                        1,
                        "Phase 1",
                        "Legacy phase",
                        List.of("docs"),
                        List.of("keep it safe"))),
                List.of(),
                "rules",
                "notes");

        final CanonicalBlueprint canonicalBlueprint = adapter.toCanonicalBlueprint(legacyBlueprintPlan);

        assertThat(canonicalBlueprint.contractVersion()).isEqualTo(CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
        assertThat(canonicalBlueprint.phases()).hasSize(1);
        assertThat(canonicalBlueprint.phases().getFirst().objective()).isEqualTo("Legacy phase");
        assertThat(canonicalBlueprint.workstreams()).isEmpty();
    }
}
