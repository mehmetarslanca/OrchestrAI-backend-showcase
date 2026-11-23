package com.arslanca.orchestrai.business.planning.blueprint;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.delivery.composition.AgentDeliveryContext;
import com.arslanca.orchestrai.business.delivery.composition.AgentDeliveryContextFactory;
import com.arslanca.orchestrai.business.delivery.composition.DeliveryDocumentIdFactory;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;

@Component
public class BlueprintPlanCompatibilityAdapter {

    private static final String LEGACY_PHASE_CONTRACT_VERSION = "1.0";

    public BlueprintPlan toBlueprintPlan(final CanonicalBlueprint canonical) {
        if (canonical == null) {
            return null;
        }

        final List<BlueprintPlan.WorkstreamDefinition> workstreams = canonical.workstreams().stream()
                .map(this::toWorkstreamDefinition)
                .toList();

        final List<BlueprintPlan.PhaseDefinition> phases = canonical.phases().stream()
                .map(this::toPhaseDefinition)
                .toList();

        final List<BlueprintPlan.AgentDefinition> agents = canonical.agents().stream()
                .map(this::toAgentDefinition)
                .toList();

        return new BlueprintPlan(
                canonical.projectName(),
                canonical.executiveSummary(),
                canonical.architectureOverview(),
                canonical.systemContext(),
                canonical.majorArchitectureDecisions(),
                workstreams,
                canonical.nonFunctionalRequirements(),
                canonical.crossCuttingConcerns(),
                canonical.glossary(),
                canonical.assumptions(),
                canonical.unknowns(),
                buildPreviewManifest(canonical, phases, agents),
                BlueprintPlan.CURRENT_CONTRACT_VERSION,
                phases,
                agents,
                canonical.ideRules(),
                canonical.starterNotes());
    }

    public CanonicalBlueprint toCanonicalBlueprint(final BlueprintPlan plan) {
        if (plan == null) {
            return null;
        }

        final List<CanonicalWorkstream> workstreams = plan.workstreams().stream()
                .map(this::toCanonicalWorkstream)
                .toList();

        final List<CanonicalPhase> phases = plan.phases().stream()
                .map(this::toCanonicalPhase)
                .toList();

        final List<CanonicalAgent> agents = plan.agents().stream()
                .map(this::toCanonicalAgent)
                .toList();

        return new CanonicalBlueprint(
                plan.projectName(),
                plan.executiveSummary(),
                plan.architectureOverview(),
                plan.systemContext(),
                plan.majorArchitectureDecisions(),
                workstreams,
                plan.nonFunctionalRequirements(),
                plan.crossCuttingConcerns(),
                plan.glossary(),
                plan.assumptions(),
                plan.unknowns(),
                phases,
                agents,
                plan.ideRules(),
                plan.starterNotes(),
                CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
    }

    private BlueprintPlan.WorkstreamDefinition toWorkstreamDefinition(final CanonicalWorkstream workstream) {
        return new BlueprintPlan.WorkstreamDefinition(
                workstream.title(),
                workstream.goal(),
                workstream.whyItExists(),
                workstream.includedScope(),
                workstream.excludedScope(),
                workstream.dependencies(),
                workstream.suggestedAgents(),
                workstream.phases().stream().map(this::toPhaseDefinition).toList());
    }

    private CanonicalWorkstream toCanonicalWorkstream(final BlueprintPlan.WorkstreamDefinition workstream) {
        return new CanonicalWorkstream(
                workstream.title(),
                workstream.goal(),
                workstream.whyItExists(),
                workstream.includedScope(),
                workstream.excludedScope(),
                workstream.dependencies(),
                workstream.suggestedAgents(),
                workstream.phases().stream().map(this::toCanonicalPhase).toList());
    }

    private BlueprintPlan.PhaseDefinition toPhaseDefinition(final CanonicalPhase phase) {
        return new BlueprintPlan.PhaseDefinition(
                phase.order(),
                phase.title(),
                phase.objective(),
                phase.whyThisPhaseExists(),
                phase.scopeIn(),
                phase.scopeOut(),
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
                phase.handoffInstructions(),
                LEGACY_PHASE_CONTRACT_VERSION,
                Map.of(),
                phase.objective(),
                List.of());
    }

    private CanonicalPhase toCanonicalPhase(final BlueprintPlan.PhaseDefinition phase) {
        final String objective = phase.objective().isBlank() ? phase.description() : phase.objective();
        return new CanonicalPhase(
                phase.order(),
                phase.title(),
                objective,
                phase.whyThisPhaseExists(),
                phase.scopeIn(),
                phase.scopeOut(),
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
                phase.handoffInstructions());
    }

    private BlueprintPlan.AgentDefinition toAgentDefinition(final CanonicalAgent agent) {
        return new BlueprintPlan.AgentDefinition(
                agent.role(),
                agent.systemPrompt(),
                agent.responsibilities());
    }

    private CanonicalAgent toCanonicalAgent(final BlueprintPlan.AgentDefinition agent) {
        return new CanonicalAgent(
                agent.role(),
                agent.systemPrompt(),
                agent.responsibilities());
    }

    private Map<String, Object> buildPreviewManifest(
            final CanonicalBlueprint canonical,
            final List<BlueprintPlan.PhaseDefinition> phases,
            final List<BlueprintPlan.AgentDefinition> agents) {
        final Map<String, Object> sectionAvailability = new LinkedHashMap<>();
        sectionAvailability.put("executiveSummary", !canonical.executiveSummary().isBlank());
        sectionAvailability.put("systemContext", !canonical.systemContext().isBlank());
        sectionAvailability.put("majorArchitectureDecisions", !canonical.majorArchitectureDecisions().isEmpty());
        sectionAvailability.put("workstreams", !canonical.workstreams().isEmpty());
        sectionAvailability.put("nonFunctionalRequirements", !canonical.nonFunctionalRequirements().isEmpty());
        sectionAvailability.put("crossCuttingConcerns", !canonical.crossCuttingConcerns().isEmpty());
        sectionAvailability.put("glossary", !canonical.glossary().isEmpty());
        sectionAvailability.put("assumptions", !canonical.assumptions().isEmpty());
        sectionAvailability.put("unknowns", !canonical.unknowns().isEmpty());
        sectionAvailability.put("starterNotes", !canonical.starterNotes().isBlank());
        sectionAvailability.put("phases", !phases.isEmpty());
        sectionAvailability.put("agents", !agents.isEmpty());

        final Map<String, Object> documentInventory = new LinkedHashMap<>();
        documentInventory.put("architectDocumentId", DeliveryDocumentIdFactory.architectId());
        documentInventory.put("globalRulesDocumentId",
                canonical.ideRules().isBlank() ? "" : DeliveryDocumentIdFactory.globalRulesId());
        documentInventory.put("phaseDocumentIds", phases.stream()
                .map(phase -> DeliveryDocumentIdFactory.phaseId(phase.order(), phase.title()))
                .toList());
        documentInventory.put("agentDocumentIds", agents.stream()
                .map(agent -> DeliveryDocumentIdFactory.agentId(agent.role()))
                .toList());

        final Map<String, Object> previewManifest = new LinkedHashMap<>();
        previewManifest.put("contractVersion", BlueprintPlan.CURRENT_CONTRACT_VERSION);
        previewManifest.put("parityScope", "logical_document_inventory");
        previewManifest.put("workstreamCount", canonical.workstreams().size());
        previewManifest.put("phaseCount", phases.size());
        previewManifest.put("agentCount", agents.size());
        previewManifest.put("assumptionCount", canonical.assumptions().size());
        previewManifest.put("unknownCount", canonical.unknowns().size());
        previewManifest.put("workstreams", canonical.workstreams().stream().map(CanonicalWorkstream::title).toList());
        previewManifest.put("phases", phases.stream().map(BlueprintPlan.PhaseDefinition::title).toList());
        previewManifest.put("agents", agents.stream().map(BlueprintPlan.AgentDefinition::role).toList());
        previewManifest.put("architectureDecisionSummaries", canonical.majorArchitectureDecisions());
        previewManifest.put("assumptions", canonical.assumptions());
        previewManifest.put("unknowns", canonical.unknowns());
        previewManifest.put("starterSummary", canonical.starterNotes());
        previewManifest.put("deliveryWarnings", buildDeliveryWarnings(canonical, phases, agents));
        previewManifest.put("workstreamSummaries", buildWorkstreamSummaries(canonical));
        previewManifest.put("phaseSummaries", buildPhaseSummaries(phases));
        previewManifest.put("agentSummaries", buildAgentSummaries(canonical, phases, agents));
        previewManifest.put("sectionAvailability", sectionAvailability);
        previewManifest.put("documentInventory", documentInventory);
        return Map.copyOf(previewManifest);
    }

    private List<Map<String, Object>> buildWorkstreamSummaries(final CanonicalBlueprint canonical) {
        return canonical.workstreams().stream()
                .map(workstream -> Map.<String, Object>of(
                        "title", workstream.title(),
                        "goal", workstream.goal(),
                        "whyItExists", workstream.whyItExists(),
                        "includedScope", workstream.includedScope(),
                        "excludedScope", workstream.excludedScope(),
                        "dependencies", workstream.dependencies(),
                        "suggestedAgents", workstream.suggestedAgents(),
                        "phaseDocumentIds", workstream.phases().stream()
                                .map(phase -> DeliveryDocumentIdFactory.phaseId(phase.order(), phase.title()))
                                .toList()))
                .toList();
    }

    private List<Map<String, Object>> buildPhaseSummaries(final List<BlueprintPlan.PhaseDefinition> phases) {
        return phases.stream()
                .map(phase -> {
                    final Map<String, Object> phaseSummary = new LinkedHashMap<>();
                    phaseSummary.put("documentId", DeliveryDocumentIdFactory.phaseId(phase.order(), phase.title()));
                    phaseSummary.put("order", phase.order());
                    phaseSummary.put("title", phase.title());
                    phaseSummary.put("purpose", phase.objective().isBlank() ? phase.description() : phase.objective());
                    phaseSummary.put("dependencies", phase.prerequisites());
                    phaseSummary.put("deliverables", phase.deliverables());
                    phaseSummary.put("contracts", phase.contracts());
                    phaseSummary.put("securityConsiderations", phase.securityConsiderations());
                    phaseSummary.put("testingStrategy", phase.testingStrategy());
                    phaseSummary.put("acceptanceCriteria", phase.acceptanceCriteria());
                    phaseSummary.put("risks", phase.risks());
                    return Map.copyOf(phaseSummary);
                })
                .toList();
    }

    private List<Map<String, Object>> buildAgentSummaries(
            final CanonicalBlueprint canonical,
            final List<BlueprintPlan.PhaseDefinition> phases,
            final List<BlueprintPlan.AgentDefinition> agents) {
        final BlueprintPlan transientPlan = new BlueprintPlan(
                canonical.projectName(),
                canonical.executiveSummary(),
                canonical.architectureOverview(),
                canonical.systemContext(),
                canonical.majorArchitectureDecisions(),
                canonical.workstreams().stream().map(this::toWorkstreamDefinition).toList(),
                canonical.nonFunctionalRequirements(),
                canonical.crossCuttingConcerns(),
                canonical.glossary(),
                canonical.assumptions(),
                canonical.unknowns(),
                Map.of(),
                BlueprintPlan.CURRENT_CONTRACT_VERSION,
                phases,
                agents,
                canonical.ideRules(),
                canonical.starterNotes());
        return agents.stream()
                .map(agent -> buildAgentSummary(transientPlan, agent))
                .toList();
    }

    private Map<String, Object> buildAgentSummary(
            final BlueprintPlan plan,
            final BlueprintPlan.AgentDefinition agent) {
        final AgentDeliveryContext context = AgentDeliveryContextFactory.resolve(plan, agent);
        return Map.of(
                "documentId", DeliveryDocumentIdFactory.agentId(agent.role()),
                "role", agent.role(),
                "mission", context.mission(),
                "whenToUse", context.whenToUse(),
                "responsibilities", agent.responsibilities(),
                "relatedWorkstreams", context.relatedWorkstreams(),
                "relatedPhaseDocumentIds", context.relatedPhaseDocumentIds(),
                "allowedScope", context.allowedScope(),
                "requiredInputs", context.requiredInputs(),
                "expectedOutputs", context.expectedOutputs());
    }

    private List<String> buildDeliveryWarnings(
            final CanonicalBlueprint canonical,
            final List<BlueprintPlan.PhaseDefinition> phases,
            final List<BlueprintPlan.AgentDefinition> agents) {
        final Map<String, Boolean> warningIndex = new LinkedHashMap<>();
        canonical.assumptions().forEach(value -> warningIndex.put("Validate assumption: " + value, true));
        canonical.unknowns().forEach(value -> warningIndex.put("Resolve unknown: " + value, true));
        if (phases.isEmpty()) {
            warningIndex.put("No phases were produced for implementation handoff.", true);
        }
        if (agents.isEmpty()) {
            warningIndex.put("No agent handoff documents were produced for this delivery.", true);
        }
        if (!canonical.starterNotes().isBlank() && canonical.starterNotes().toLowerCase().contains("docs-only")) {
            warningIndex.put("Starter resolution is operating in docs-only mode.", true);
        }
        return List.copyOf(warningIndex.keySet());
    }
}
