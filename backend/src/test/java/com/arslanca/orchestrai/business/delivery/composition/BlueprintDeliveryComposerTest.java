package com.arslanca.orchestrai.business.delivery.composition;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifestAssembler;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;

class BlueprintDeliveryComposerTest {

    private final BlueprintDeliveryComposer composer = new BlueprintDeliveryComposer(
            new ArchitectDocumentComposer(),
            new PhaseDocumentComposer(),
            new AgentDocumentComposer(),
            new DeliveryManifestAssembler());

    @Test
    void shouldComposeLogicalDocumentsAndManifestInventory() {
        final DeliveryComposition composition = composer.compose(blueprint());

        assertThat(composition.documents()).hasSize(4);
        assertThat(composition.documents())
                .extracting(ComposedDocument::id)
                .containsExactly("rules", "architect", "phase_1_platform_foundation", "backend-engineer");
        assertThat(composition.manifest().totalDocuments()).isEqualTo(4);
        assertThat(composition.manifest().projectName()).isEqualTo("OrchestrAI");
        assertThat(composition.manifest().inventory())
                .extracting(entry -> entry.id())
                .containsExactly("rules", "architect", "phase_1_platform_foundation", "backend-engineer");
        assertThat(composition.manifest().inventory().get(1).sections())
                .contains("executive-summary", "architecture-overview", "system-context");
    }

    private BlueprintPlan blueprint() {
        final BlueprintPlan.PhaseDefinition phase = new BlueprintPlan.PhaseDefinition(
                1,
                "Platform Foundation",
                "Stand up the platform backbone",
                List.of("starter project", "persistence setup"),
                List.of("no direct prod rollout"));
        final BlueprintPlan.AgentDefinition agent = new BlueprintPlan.AgentDefinition(
                "Backend Engineer",
                "Own backend delivery seams.",
                List.of("implement services", "protect contracts"));

        return new BlueprintPlan(
                "OrchestrAI",
                "Executive summary",
                "Architecture overview",
                "System context",
                List.of("Use typed seams"),
                List.<BlueprintPlan.WorkstreamDefinition>of(),
                List.of("availability"),
                List.of("security"),
                List.of("planner"),
                List.of(phase),
                List.of(agent),
                "Global rules",
                "Starter note");
    }
}
