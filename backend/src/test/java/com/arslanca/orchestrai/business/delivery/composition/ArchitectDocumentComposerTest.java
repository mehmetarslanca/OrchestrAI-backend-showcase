package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ArchitectDocumentComposerTest {

    private final ArchitectDocumentComposer composer = new ArchitectDocumentComposer();

    @Test
    @DisplayName("Should compose architecture overview document correctly")
    void compose_ShouldComposeArchitectDocument() {
        var phase = new BlueprintPlan.PhaseDefinition(1, "Phase One", "Test description", List.of("Del 1"), List.of("Const 1"));
        var workstream = new BlueprintPlan.WorkstreamDefinition(
                "Backend Core",
                "Ship backend",
                "Needed for delivery",
                List.of("api", "auth"),
                List.of("mobile"),
                List.of("starter"),
                List.of("backend-engineer"),
                List.of(phase));
        var plan = new BlueprintPlan(
                "Test Project",
                "Exec Sum",
                "Arch Overview",
                "Sys Context",
                List.of("Dec 1"),
                List.of(workstream),
                List.of("NFR 1"),
                List.of("CCC 1"),
                List.of("Glossary 1"),
                List.of(phase),
                List.of(),
                "IDE Rules",
                "Starter Notes"
        );

        ComposedDocument document = composer.compose(plan);

        assertThat(document.kind()).isEqualTo(DocumentKind.ARCHITECTURE_OVERVIEW);
        assertThat(document.id()).isEqualTo(DeliveryDocumentIdFactory.architectId());
        assertThat(document.title()).isEqualTo("System Architecture");
        assertThat(document.content()).contains("# Test Project - System Architecture");
        assertThat(document.content()).contains("## Executive Summary\n\nExec Sum");
        assertThat(document.content()).contains("## Architecture Overview\n\nArch Overview");
        assertThat(document.content()).contains("## System Context\n\nSys Context");
        assertThat(document.content()).contains("## Major Architecture Decisions\n\n- Dec 1");
        assertThat(document.content()).contains("## Workstream Map");
        assertThat(document.content()).contains("### Backend Core");
        assertThat(document.content()).contains("## Global Constraints\n\n- Const 1");
        assertThat(document.content()).contains("## Starter Notes\n\nStarter Notes");
        assertThat(document.content()).contains("## Development Phases\n\n### Phase 1: Phase One\nTest description");

        assertThat(document.sections()).containsExactly(
                "executive-summary",
                "architecture-overview",
                "system-context",
                "major-architecture-decisions",
                "workstream-map",
                "global-constraints",
                "non-functional-requirements",
                "cross-cutting-concerns",
                "glossary",
                "starter-notes",
                "development-phases"
        );
    }
}
