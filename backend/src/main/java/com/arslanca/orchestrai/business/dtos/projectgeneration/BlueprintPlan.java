package com.arslanca.orchestrai.business.dtos.projectgeneration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record BlueprintPlan(
        String projectName,
        String executiveSummary,
        String architectureOverview,
        String systemContext,
        List<String> majorArchitectureDecisions,
        List<WorkstreamDefinition> workstreams,
        List<String> nonFunctionalRequirements,
        List<String> crossCuttingConcerns,
        List<String> glossary,
        List<String> assumptions,
        List<String> unknowns,
        Map<String, Object> previewManifest,
        String contractVersion,
        
        // Legacy support
        List<PhaseDefinition> phases,
        List<AgentDefinition> agents,

        String ideRules,
        String starterNotes) {
    public static final String CURRENT_CONTRACT_VERSION = "2.0";

    public BlueprintPlan(
            String projectName,
            String architectureOverview,
            List<PhaseDefinition> phases,
            List<AgentDefinition> agents,
            String ideRules,
            String starterNotes) {
        this(projectName, "", architectureOverview, "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Map.of(), CURRENT_CONTRACT_VERSION, phases, agents, ideRules, starterNotes);
    }

    public BlueprintPlan(
            String projectName,
            String executiveSummary,
            String architectureOverview,
            String systemContext,
            List<String> majorArchitectureDecisions,
            List<WorkstreamDefinition> workstreams,
            List<String> nonFunctionalRequirements,
            List<String> crossCuttingConcerns,
            List<String> glossary,
            List<PhaseDefinition> phases,
            List<AgentDefinition> agents,
            String ideRules,
            String starterNotes) {
        this(projectName, executiveSummary, architectureOverview, systemContext, majorArchitectureDecisions, workstreams,
                nonFunctionalRequirements, crossCuttingConcerns, glossary, List.of(), List.of(), Map.of(),
                CURRENT_CONTRACT_VERSION, phases, agents, ideRules, starterNotes);
    }

    public BlueprintPlan {
        projectName = normalizeText(projectName);
        executiveSummary = normalizeText(executiveSummary);
        architectureOverview = normalizeText(architectureOverview);
        systemContext = normalizeText(systemContext);
        majorArchitectureDecisions = majorArchitectureDecisions == null ? List.of() : List.copyOf(majorArchitectureDecisions);
        workstreams = workstreams == null ? List.of() : List.copyOf(workstreams);
        nonFunctionalRequirements = nonFunctionalRequirements == null ? List.of() : List.copyOf(nonFunctionalRequirements);
        crossCuttingConcerns = crossCuttingConcerns == null ? List.of() : List.copyOf(crossCuttingConcerns);
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
        previewManifest = previewManifest == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(previewManifest));
        contractVersion = contractVersion == null || contractVersion.isBlank() ? CURRENT_CONTRACT_VERSION : contractVersion.trim();

        phases = phases == null || phases.isEmpty() ? flattenWorkstreamPhases(workstreams) : List.copyOf(phases);
        agents = agents == null ? List.of() : List.copyOf(agents);
        starterNotes = normalizeText(starterNotes);
        ideRules = normalizeText(ideRules);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkstreamDefinition(
            String title,
            String goal,
            String whyItExists,
            List<String> includedScope,
            List<String> excludedScope,
            List<String> dependencies,
            List<String> suggestedAgents,
            List<PhaseDefinition> phases) {
        public WorkstreamDefinition {
            title = normalizeText(title);
            goal = normalizeText(goal);
            whyItExists = normalizeText(whyItExists);
            includedScope = includedScope == null ? List.of() : List.copyOf(includedScope);
            excludedScope = excludedScope == null ? List.of() : List.copyOf(excludedScope);
            dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
            suggestedAgents = suggestedAgents == null ? List.of() : List.copyOf(suggestedAgents);
            phases = phases == null ? List.of() : List.copyOf(phases);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhaseDefinition(
            int order,
            String title,
            String objective,
            String whyThisPhaseExists,
            List<String> scopeIn,
            List<String> scopeOut,
            List<String> prerequisites,
            List<String> deliverables,
            List<String> architectureDecisions,
            List<String> contracts,
            String dataImpact,
            List<String> securityConsiderations,
            String observabilityPlan,
            String testingStrategy,
            List<String> acceptanceCriteria,
            List<String> risks,
            String handoffInstructions,
            String contractVersion,
            Map<String, Object> previewManifest,
            
            // Legacy support
            String description,
            List<String> constraints) {

        public PhaseDefinition(
                int order,
                String title,
                String description,
                List<String> deliverables,
                List<String> constraints) {
            this(order, title, "", "", List.of(), List.of(), List.of(), deliverables, List.of(), List.of(), "", List.of(), "", "", List.of(), List.of(), "", CURRENT_CONTRACT_VERSION, Map.of(), description, constraints);
        }

        public PhaseDefinition {
            title = normalizeText(title);
            objective = normalizeText(objective);
            whyThisPhaseExists = normalizeText(whyThisPhaseExists);
            scopeIn = scopeIn == null ? List.of() : List.copyOf(scopeIn);
            scopeOut = scopeOut == null ? List.of() : List.copyOf(scopeOut);
            prerequisites = prerequisites == null ? List.of() : List.copyOf(prerequisites);
            deliverables = deliverables == null ? List.of() : List.copyOf(deliverables);
            architectureDecisions = architectureDecisions == null ? List.of() : List.copyOf(architectureDecisions);
            contracts = contracts == null ? List.of() : List.copyOf(contracts);
            dataImpact = normalizeText(dataImpact);
            securityConsiderations = securityConsiderations == null ? List.of() : List.copyOf(securityConsiderations);
            observabilityPlan = normalizeText(observabilityPlan);
            testingStrategy = normalizeText(testingStrategy);
            acceptanceCriteria = acceptanceCriteria == null ? List.of() : List.copyOf(acceptanceCriteria);
            risks = risks == null ? List.of() : List.copyOf(risks);
            handoffInstructions = normalizeText(handoffInstructions);
            contractVersion = contractVersion == null || contractVersion.isBlank() ? CURRENT_CONTRACT_VERSION : contractVersion.trim();
            previewManifest = previewManifest == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(previewManifest));

            description = normalizeText(description).isBlank() ? normalizeText(objective) : normalizeText(description);
            constraints = constraints == null ? List.of() : List.copyOf(constraints);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentDefinition(
            String role,
            String systemPrompt,
            List<String> responsibilities) {

        public AgentDefinition {
            role = normalizeText(role);
            systemPrompt = normalizeText(systemPrompt);
            responsibilities = responsibilities == null ? List.of() : List.copyOf(responsibilities);
        }
    }

    private static List<PhaseDefinition> flattenWorkstreamPhases(final List<WorkstreamDefinition> workstreams) {
        if (workstreams == null || workstreams.isEmpty()) {
            return List.of();
        }

        final List<PhaseDefinition> flattenedPhases = new ArrayList<>();
        for (final WorkstreamDefinition workstream : workstreams) {
            if (workstream == null || workstream.phases() == null) {
                continue;
            }
            flattenedPhases.addAll(workstream.phases());
        }
        return List.copyOf(flattenedPhases);
    }

    private static String normalizeText(final String value) {
        if (value == null) {
            return "";
        }
        return value.trim();
    }
}
