package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

import com.arslanca.orchestrai.business.planning.blueprint.CanonicalWorkstream;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalAgent;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public final class MultiPassPlanningArtifacts {

    private MultiPassPlanningArtifacts() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkstreamSeed(
            String title,
            String goal,
            String whyItExists) {
        public WorkstreamSeed {
            title = title == null ? "" : title.trim();
            goal = goal == null ? "" : goal.trim();
            whyItExists = whyItExists == null ? "" : whyItExists.trim();
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ArchitectureFramingPass(
            String projectName,
            String executiveSummary,
            String architectureOverview,
            String systemContext,
            List<String> majorArchitectureDecisions,
            List<String> nonFunctionalRequirements,
            List<String> crossCuttingConcerns,
            List<String> glossary,
            List<WorkstreamSeed> workstreams) {
        public ArchitectureFramingPass {
            projectName = projectName == null ? "" : projectName.trim();
            executiveSummary = executiveSummary == null ? "" : executiveSummary.trim();
            architectureOverview = architectureOverview == null ? "" : architectureOverview.trim();
            systemContext = systemContext == null ? "" : systemContext.trim();
            majorArchitectureDecisions = majorArchitectureDecisions == null ? List.of() : List.copyOf(majorArchitectureDecisions);
            nonFunctionalRequirements = nonFunctionalRequirements == null ? List.of() : List.copyOf(nonFunctionalRequirements);
            crossCuttingConcerns = crossCuttingConcerns == null ? List.of() : List.copyOf(crossCuttingConcerns);
            glossary = glossary == null ? List.of() : List.copyOf(glossary);
            workstreams = workstreams == null ? List.of() : List.copyOf(workstreams);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record WorkstreamDecompositionPass(
            List<CanonicalWorkstream> workstreams) {
        public WorkstreamDecompositionPass {
            workstreams = workstreams == null ? List.of() : List.copyOf(workstreams);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhaseExpansionPass(
            List<CanonicalWorkstream> workstreams) {
        public PhaseExpansionPass {
            workstreams = workstreams == null ? List.of() : List.copyOf(workstreams);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentGenerationPass(
            List<CanonicalAgent> agents) {
        public AgentGenerationPass {
            agents = agents == null ? List.of() : List.copyOf(agents);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record IdeRulesPass(
            String ideRules,
            String starterNotes) {
        public IdeRulesPass {
            ideRules = ideRules == null ? "" : ideRules.trim();
            starterNotes = starterNotes == null ? "" : starterNotes.trim();
        }
    }
}
