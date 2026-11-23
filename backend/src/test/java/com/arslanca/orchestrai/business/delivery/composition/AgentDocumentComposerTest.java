package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AgentDocumentComposerTest {

    private final AgentDocumentComposer composer = new AgentDocumentComposer();

    @Test
    @DisplayName("Should compose agent document correctly")
    void compose_ShouldComposeAgentDocument() {
        var agent = new BlueprintPlan.AgentDefinition("Test Agent", "You are a test agent.", List.of("Test things"));
        var phase = new BlueprintPlan.PhaseDefinition(
                1,
                "Delivery",
                "Ship the delivery seam",
                "Needed to finish artifact delivery",
                List.of("delivery composition"),
                List.of("ui polish"),
                List.of("approved starter"),
                List.of("delivery manifest"),
                List.of("typed seam"),
                List.of("delivery contract"),
                "Persists manifest metadata",
                List.of("validate path parity"),
                "Track manifest/schema mismatches",
                "Unit tests for manifest and strategy parity",
                List.of("rendered paths match logical docs"),
                List.of("parity drift"),
                "handoff to QA",
                "2.0",
                java.util.Map.of(),
                "Ship the delivery seam",
                List.of("No API breakage"));
        var workstream = new BlueprintPlan.WorkstreamDefinition(
                "Agent Handoff",
                "Ship rich handoff docs",
                "Needed for IDE parity",
                List.of("delivery composition", "agent handoff docs"),
                List.of("ui polish"),
                List.of("approved starter"),
                List.of("test-agent"),
                List.of(phase));
        var plan = new BlueprintPlan(
                "Test Project",
                "Executive summary",
                "Arch",
                "Context",
                List.of("Use typed delivery contracts"),
                List.of(workstream),
                List.of("availability"),
                List.of("security"),
                List.of("glossary"),
                List.of(phase),
                List.of(agent),
                "IDE Rules",
                "Starter Notes");

        List<ComposedDocument> documents = composer.compose(plan);

        assertThat(documents).hasSize(1);
        ComposedDocument document = documents.get(0);

        assertThat(document.kind()).isEqualTo(DocumentKind.AGENT);
        assertThat(document.id()).isEqualTo(DeliveryDocumentIdFactory.agentId("Test Agent"));
        assertThat(document.title()).isEqualTo("Test Agent");
        assertThat(document.content()).contains("# Test Agent");
        assertThat(document.content()).contains("## Mission");
        assertThat(document.content()).contains("## When To Use This Agent");
        assertThat(document.content()).contains("## Allowed Scope");
        assertThat(document.content()).contains("## Forbidden Scope");
        assertThat(document.content()).contains("## Required Inputs");
        assertThat(document.content()).contains("## Expected Outputs");
        assertThat(document.content()).contains("## Quality Checklist");
        assertThat(document.content()).contains("## Handoff Rules");
        assertThat(document.content()).contains("## System Prompt\n\nYou are a test agent.");
        assertThat(document.content()).contains("## Responsibilities\n\n- Test things");
        assertThat(document.sections()).contains(
                "mission",
                "when-to-use",
                "allowed-scope",
                "forbidden-scope",
                "required-inputs",
                "expected-outputs",
                "quality-checklist",
                "handoff-rules",
                "system-prompt",
                "responsibilities");
    }
}
