package com.arslanca.orchestrai.business.concretes.delivery.strategies;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.delivery.composition.AgentDocumentComposer;
import com.arslanca.orchestrai.business.delivery.composition.ArchitectDocumentComposer;
import com.arslanca.orchestrai.business.delivery.composition.BlueprintDeliveryComposer;
import com.arslanca.orchestrai.business.delivery.composition.PhaseDocumentComposer;
import com.arslanca.orchestrai.business.delivery.manifest.DeliveryManifestAssembler;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;

class GenericDeliveryStrategyTest {

    private GenericDeliveryStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new GenericDeliveryStrategy();
    }

    @Test
    void shouldReturnCorrectSupportedIde() {
        assertThat(strategy.supportedIde()).isEqualTo("generic");
    }

    @Test
    void shouldTransformBlueprintToGenericFormat() {
        final BlueprintPlan blueprint = blueprint();

        final var layout = strategy.transform(composer().compose(blueprint));
        final List<FileNode> files = layout.files();

        assertThat(files).hasSize(4);
        assertThat(files).extracting(FileNode::path)
                .containsExactlyInAnyOrder(
                        "docs/architect.md",
                        "docs/rules.md",
                        "docs/phases/phase_1_phase_1.md",
                        "docs/agents/agent-role.md");
        assertThat(layout.renderedDocumentPaths()).containsEntry("agent-role", "docs/agents/agent-role.md");
    }

    @Test
    void shouldIncludeStarterNotesInArchitectDocument() {
        final FileNode architectFile = strategy.transform(composer().compose(blueprint())).files().stream()
                .filter(file -> file.path().equals("docs/architect.md"))
                .findFirst()
                .orElseThrow();

        assertThat(architectFile.content()).contains("Starter Notes");
        assertThat(architectFile.content()).contains("Use deterministic starter assembly");
    }

    @Test
    void shouldRenderSharedAgentDocumentContent() {
        final FileNode agentFile = strategy.transform(composer().compose(blueprint())).files().stream()
                .filter(file -> file.path().equals("docs/agents/agent-role.md"))
                .findFirst()
                .orElseThrow();

        assertThat(agentFile.content()).contains("## Mission");
        assertThat(agentFile.content()).contains("## System Prompt");
        assertThat(agentFile.content()).contains("## Responsibilities");
    }

    private BlueprintPlan blueprint() {
        final BlueprintPlan.PhaseDefinition phase = new BlueprintPlan.PhaseDefinition(
                1,
                "Phase 1",
                "desc",
                List.of("deliverable"),
                List.of("constraint"));
        final BlueprintPlan.AgentDefinition agent = new BlueprintPlan.AgentDefinition(
                "Agent Role",
                "prompt",
                List.of("responsibility"));

        return new BlueprintPlan("Project", "Arch", List.of(phase), List.of(agent), "Rules", "Use deterministic starter assembly");
    }

    private BlueprintDeliveryComposer composer() {
        return new BlueprintDeliveryComposer(
                new ArchitectDocumentComposer(),
                new PhaseDocumentComposer(),
                new AgentDocumentComposer(),
                new DeliveryManifestAssembler());
    }
}
