package com.arslanca.orchestrai.business.delivery.manifest;

import com.arslanca.orchestrai.business.delivery.composition.AgentDeliveryContext;
import com.arslanca.orchestrai.business.delivery.composition.AgentDeliveryContextFactory;
import com.arslanca.orchestrai.business.delivery.composition.ComposedDocument;
import com.arslanca.orchestrai.business.delivery.composition.DeliveryDocumentIdFactory;
import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;

import java.util.ArrayList;
import java.util.List;

@Component
public class DeliveryManifestAssembler {

    private static final String SCHEMA_VERSION = "2.0";

    public DeliveryManifest assemble(final BlueprintPlan plan, final List<ComposedDocument> documents) {
        final List<ManifestDocumentEntry> entries = documents.stream()
                .map(doc -> new ManifestDocumentEntry(
                        doc.kind(),
                        doc.id(),
                        doc.title(),
                        doc.sections(),
                        doc.content().length(),
                        null))
                .toList();

        return new DeliveryManifest(
                SCHEMA_VERSION,
                plan == null ? "" : plan.projectName(),
                "",
                plan == null ? "" : plan.executiveSummary(),
                plan == null ? "" : plan.architectureOverview(),
                plan == null ? "" : plan.systemContext(),
                plan == null ? List.of() : plan.majorArchitectureDecisions(),
                buildWorkstreams(plan),
                buildPhases(plan),
                buildAgents(plan),
                plan == null ? List.of() : plan.assumptions(),
                plan == null ? List.of() : plan.unknowns(),
                buildGlobalConstraints(plan),
                plan == null ? "" : plan.starterNotes(),
                buildDeliveryWarnings(plan),
                entries.size(),
                entries);
    }

    private List<ManifestWorkstreamEntry> buildWorkstreams(final BlueprintPlan plan) {
        if (plan == null || plan.workstreams() == null) {
            return List.of();
        }

        return plan.workstreams().stream()
                .map(workstream -> new ManifestWorkstreamEntry(
                        workstream.title(),
                        workstream.goal(),
                        workstream.whyItExists(),
                        workstream.includedScope(),
                        workstream.excludedScope(),
                        workstream.dependencies(),
                        workstream.suggestedAgents(),
                        workstream.phases().stream()
                                .map(phase -> DeliveryDocumentIdFactory.phaseId(phase.order(), phase.title()))
                                .toList()))
                .toList();
    }

    private List<ManifestPhaseEntry> buildPhases(final BlueprintPlan plan) {
        if (plan == null || plan.phases() == null) {
            return List.of();
        }

        return plan.phases().stream()
                .map(phase -> new ManifestPhaseEntry(
                        DeliveryDocumentIdFactory.phaseId(phase.order(), phase.title()),
                        phase.order(),
                        phase.title(),
                        phase.objective().isBlank() ? phase.description() : phase.objective(),
                        phase.whyThisPhaseExists(),
                        phase.prerequisites(),
                        phase.deliverables(),
                        phase.architectureDecisions(),
                        phase.contracts(),
                        phase.dataImpact(),
                        phase.securityConsiderations(),
                        phase.observabilityPlan(),
                        phase.testingStrategy(),
                        phase.acceptanceCriteria(),
                        phase.risks(),
                        phase.handoffInstructions()))
                .toList();
    }

    private List<ManifestAgentEntry> buildAgents(final BlueprintPlan plan) {
        if (plan == null || plan.agents() == null) {
            return List.of();
        }

        return plan.agents().stream()
                .map(agent -> toManifestAgentEntry(plan, agent))
                .toList();
    }

    private ManifestAgentEntry toManifestAgentEntry(
            final BlueprintPlan plan,
            final BlueprintPlan.AgentDefinition agent) {
        final AgentDeliveryContext context = AgentDeliveryContextFactory.resolve(plan, agent);
        return new ManifestAgentEntry(
                DeliveryDocumentIdFactory.agentId(agent.role()),
                agent.role(),
                context.mission(),
                context.whenToUse(),
                agent.responsibilities(),
                context.relatedWorkstreams(),
                context.relatedPhaseDocumentIds(),
                context.allowedScope(),
                context.forbiddenScope(),
                context.requiredInputs(),
                context.expectedOutputs(),
                context.qualityChecklist(),
                context.handoffRules());
    }

    private List<String> buildGlobalConstraints(final BlueprintPlan plan) {
        if (plan == null || plan.phases() == null) {
            return List.of();
        }

        return distinctStrings(plan.phases().stream()
                .flatMap(phase -> phase.constraints().stream())
                .toList());
    }

    private List<String> buildDeliveryWarnings(final BlueprintPlan plan) {
        if (plan == null) {
            return List.of();
        }

        final List<String> warnings = new ArrayList<>();
        plan.assumptions().forEach(value -> warnings.add("Validate assumption: " + value));
        plan.unknowns().forEach(value -> warnings.add("Resolve unknown: " + value));

        if (plan.phases().isEmpty()) {
            warnings.add("No phases were produced for implementation handoff.");
        }
        if (plan.agents().isEmpty()) {
            warnings.add("No agent handoff documents were produced for this delivery.");
        }
        if (plan.starterNotes() != null && plan.starterNotes().toLowerCase().contains("docs-only")) {
            warnings.add("Starter resolution is operating in docs-only mode.");
        }

        return distinctStrings(warnings);
    }

    private List<String> distinctStrings(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        final List<String> distinctValues = new ArrayList<>();
        for (final String value : values) {
            if (value != null && !value.isBlank() && !distinctValues.contains(value.trim())) {
                distinctValues.add(value.trim());
            }
        }
        return List.copyOf(distinctValues);
    }
}
