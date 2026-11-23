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

class CursorDeliveryStrategyTest {

    private CursorDeliveryStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new CursorDeliveryStrategy();
    }

    @Test
    void shouldReturnCorrectSupportedIde() {
        assertThat(strategy.supportedIde()).isEqualTo("cursor");
    }

    @Test
    void shouldTransformBlueprintToCursorFormat() {
        final var layout = strategy.transform(composer().compose(blueprint()));
        final List<FileNode> files = layout.files();

        assertThat(files).hasSize(4);
        assertThat(files).extracting(FileNode::path)
                .containsExactlyInAnyOrder(
                        ".cursorrules",
                        "docs/architect.md",
                        "docs/phases/phase_1_database_setup.md", "docs/agents/backend-engineer.md");
        assertThat(layout.renderedDocumentPaths()).containsEntry("architect", "docs/architect.md");
        assertThat(layout.renderedDocumentPaths()).containsEntry("backend-engineer", "docs/agents/backend-engineer.md");
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
    void shouldPreserveAgentResponsibilitiesForParity() {
        final FileNode agentFile = strategy.transform(composer().compose(blueprint())).files().stream()
                .filter(file -> file.path().equals("docs/agents/backend-engineer.md"))
                .findFirst()
                .orElseThrow();

        assertThat(agentFile.content()).contains("## Mission");
        assertThat(agentFile.content()).contains("## Quality Checklist");
        assertThat(agentFile.content()).contains("## Responsibilities");
        assertThat(agentFile.content()).contains("implement services");
    }

    private BlueprintPlan blueprint() {
        final BlueprintPlan.PhaseDefinition phase = new BlueprintPlan.PhaseDefinition(
                1,
                "Database setup",
                "Setup postgres",
                List.of("postgres"),
                List.of("no schema drift"));
        final BlueprintPlan.AgentDefinition agent = new BlueprintPlan.AgentDefinition(
                "Backend Engineer",
                "You write java",
                List.of("implement services"));

        return new BlueprintPlan(
                "TestProject",
                "Architecture overview text",
                List.of(phase),
                List.of(agent),
                "IDE Rules Content",
                "Use deterministic starter assembly");
    }

    private BlueprintDeliveryComposer composer() {
        return new BlueprintDeliveryComposer(
                new ArchitectDocumentComposer(),
                new PhaseDocumentComposer(),
                new AgentDocumentComposer(),
                new DeliveryManifestAssembler());
    }
}
