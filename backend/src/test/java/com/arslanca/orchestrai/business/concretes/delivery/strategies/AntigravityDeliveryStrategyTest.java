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

class AntigravityDeliveryStrategyTest {

    private AntigravityDeliveryStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new AntigravityDeliveryStrategy();
    }

    @Test
    void shouldReturnCorrectSupportedIde() {
        assertThat(strategy.supportedIde()).isEqualTo("antigravity");
    }

    @Test
    void shouldTransformBlueprintToAntigravityFormat() {
        final var layout = strategy.transform(composer().compose(blueprint()));
        final List<FileNode> files = layout.files();

        assertThat(files).hasSize(4);
        assertThat(files).extracting(FileNode::path)
                .containsExactlyInAnyOrder(
                        ".agents/rules/rules.md",
                        ".agents/workflows/security-specialist.md",
                        "docs/architect.md",
                        "docs/phases/phase_2_auth_setup.md");
        assertThat(layout.renderedDocumentPaths()).containsEntry("security-specialist", ".agents/workflows/security-specialist.md");
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
    void shouldWrapComposedAgentDocumentWithoutDroppingResponsibilities() {
        final FileNode workflowFile = strategy.transform(composer().compose(blueprint())).files().stream()
                .filter(file -> file.path().equals(".agents/workflows/security-specialist.md"))
                .findFirst()
                .orElseThrow();

        assertThat(workflowFile.content()).contains("description: Security Specialist");
        assertThat(workflowFile.content()).contains("## Mission");
        assertThat(workflowFile.content()).contains("## Responsibilities");
    }

    private BlueprintPlan blueprint() {
        final BlueprintPlan.PhaseDefinition phase = new BlueprintPlan.PhaseDefinition(
                2,
                "Auth Setup",
                "Setup JWT",
                List.of("jwt"),
                List.of("no weak secrets"));
        final BlueprintPlan.AgentDefinition agent = new BlueprintPlan.AgentDefinition(
                "Security Specialist",
                "You handle security",
                List.of("secure authentication"));

        return new BlueprintPlan(
                "TestProject",
                "Architecture Overview",
                List.of(phase),
                List.of(agent),
                "IDE Rules",
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
