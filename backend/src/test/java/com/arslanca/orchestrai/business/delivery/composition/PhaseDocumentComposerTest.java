package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PhaseDocumentComposerTest {

    private final PhaseDocumentComposer composer = new PhaseDocumentComposer();

    @Test
    @DisplayName("Should compose phase document correctly")
    void compose_ShouldComposePhaseDocument() {
        // Arrange
        var phase = new BlueprintPlan.PhaseDefinition(1, "Phase One", "Objective", "Why it exists",
                List.of("In Scope"), List.of("Out Scope"), List.of("Prereq"), List.of("Del 1"),
                List.of("Arch Dec"), List.of("Contract"), "Data Impact", List.of("Sec Const"),
                "Obs Plan", "Test Strat", List.of("AC 1"), List.of("Risk 1"), "Handoff",
                "2.0", java.util.Map.of(), "Desc", List.of("Constraint"));
        
        var plan = new BlueprintPlan("Test Project", "Arch", List.of(phase), List.of(), "IDE Rules", "Starter Notes");

        // Act
        List<ComposedDocument> documents = composer.compose(plan);

        // Assert
        assertThat(documents).hasSize(1);
        ComposedDocument document = documents.get(0);
        
        assertThat(document.kind()).isEqualTo(DocumentKind.PHASE);
        assertThat(document.id()).isEqualTo(DeliveryDocumentIdFactory.phaseId(1, "Phase One"));
        assertThat(document.title()).isEqualTo("Phase One");
        assertThat(document.content()).contains("# Phase 1: Phase One");
        assertThat(document.content()).contains("## Purpose\n\nObjective");
        assertThat(document.content()).contains("## Deliverables\n\n- Del 1");
        
        assertThat(document.sections()).contains(
                "purpose",
                "why-this-phase-exists",
                "scope-in",
                "scope-out",
                "dependencies",
                "deliverables",
                "architecture-decisions",
                "contracts",
                "data-impact",
                "security-considerations",
                "observability-plan",
                "testing-strategy",
                "acceptance-criteria",
                "risks",
                "handoff-instructions",
                "constraints"
        );
    }
}
