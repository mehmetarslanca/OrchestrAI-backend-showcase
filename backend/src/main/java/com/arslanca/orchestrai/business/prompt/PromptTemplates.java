package com.arslanca.orchestrai.business.prompt;

import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalPhase;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalWorkstream;
import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.ArchitectureFramingPass;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.PhaseExpansionPass;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.WorkstreamSeed;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.WorkstreamDecompositionPass;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterManifest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;



/**
 * User prompt templates for the dual-LLM pipeline.
 *
 * <p>
 * These templates wrap the user's raw input or structured data
 * into contextual messages sent alongside system prompts.
 * </p>
 */
public final class PromptTemplates {

    private PromptTemplates() {
    }

    /**
     * Stage 1: Wraps the raw user prompt for the Router/Analyzer.
     *
     * @param userPrompt the raw natural language project description
     * @return formatted prompt for the light model
     */
    public static String routerUserPrompt(final String userPrompt) {
        return """
                Analyze the following project description and extract structured planning information.

                ---
                %s
                ---
                """.formatted(userPrompt);
    }

    public static String chatPlanningSnapshotPrompt(
            final Optional<ChatPlanningSnapshot> previousSnapshot,
            final String recentMessages) {
        return """
                Update the rolling chat planning snapshot using the previous persisted snapshot and the latest concrete conversation delta.

                Previous persisted snapshot:
                ---
                %s
                ---

                Latest concrete messages:
                ---
                %s
                ---
                """.formatted(
                previousSnapshot.map(PromptTemplates::describeSnapshot).orElse("none"),
                recentMessages);
    }

    public static String chatPreparationAnalysisPrompt(final String planningConversationSummary) {
        return """
                Generate the canonical structured analysis for blueprint preparation using this completed planning handoff.

                ---
                %s
                ---
                """.formatted(planningConversationSummary);
    }

    /**
     * Stage 2: Formats the structured analysis for the Chief Architect.
     *
     * @param analysis  structured analysis from Stage 1
     * @param ideTarget target IDE (cursor, antigravity, windsurf, generic)
     * @return formatted prompt for the heavy model
     */
    public static String architectUserPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final String ideTarget,
            final StarterSelection starterSelection) {
        final String starterContext = buildStarterContext(starterSelection);
        return """
                Based on the following structured analysis, generate a complete plan-only development blueprint.

                Target IDE: %s

                Project Analysis:
                - Name: %s
                - Type: %s
                - Tech Stack: %s
                - Features: %s
                - Complexity: %s
                - Summary: %s
                - Actors: %s
                - Business Goals: %s
                - User Journeys: %s
                - Delivery Channels: %s
                - Frontend Needed: %s
                - Backend Needed: %s
                - Worker Needed: %s
                - Deployment Model: %s
                - Tenancy Model: %s
                - Data Sources: %s
                - Integration Points: %s
                - Security Requirements: %s
                - Compliance Requirements: %s
                - Scale Expectations: %s
                - Latency Expectations: %s
                 - Operational Constraints: %s
                 - Assumptions: %s
                 - Unknowns: %s
                 - Open Questions: %s
                 - Unresolved Constraints: %s
                 - Non Goals: %s
                 - Complexity Markers: %s
                 - Contract Version: %s

                Deterministic Planning Profile:
%s

                Deterministic Starter Resolution:
                %s

                Authoritative planning rules:
                %s

                Generate a production-ready plan with an executive summary, architecture overview, workstreams, phases,
                agent profiles, IDE rules, and starter assembly notes.
                Do not generate a source file tree or final code files.
                """.formatted(
                ideTarget,
                analysis.projectName(),
                analysis.projectType(),
                joinValues(analysis.techStack()),
                joinValues(analysis.features()),
                analysis.complexity(),
                analysis.summary(),
                joinValues(analysis.actors()),
                joinValues(analysis.businessGoals()),
                joinValues(analysis.userJourneys()),
                joinValues(analysis.deliveryChannels()),
                Boolean.toString(analysis.frontendNeededResolved()),
                Boolean.toString(analysis.backendNeededResolved()),
                Boolean.toString(analysis.workerNeededResolved()),
                safeValue(analysis.deploymentModel()),
                safeValue(analysis.tenancyModel()),
                joinValues(analysis.dataSources()),
                joinValues(analysis.integrationPoints()),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.complianceRequirements()),
                 safeValue(analysis.scaleExpectations()),
                 safeValue(analysis.latencyExpectations()),
                 joinValues(analysis.operationalConstraints()),
                 joinValues(analysis.assumptions()),
                 joinValues(analysis.unknowns()),
                 joinValues(analysis.openQuestions()),
                 joinValues(analysis.unresolvedConstraints()),
                 joinValues(analysis.nonGoals()),
                 joinValues(analysis.complexityMarkers()),
                 safeValue(analysis.contractVersion()),
                 buildContextEnvelope(profile, analysis),
                 starterContext,
                 buildPassGuardrails());
    }

    public static String buildContextEnvelope(final DeterministicPlanningProfile profile, final StructuredAnalysis analysis) {
        if (profile == null) {
            return "- Effective Complexity: unknown\n"
                    + "- Hard Signals: none\n"
                    + "- Mandatory Coverage Families: none\n"
                    + "- Expected Workstreams: unknown\n"
                    + "- Expected Phases: unknown\n"
                    + "- Expected Agents: unknown\n"
                    + "- Output Band: none\n"
                    + "- Scale Expectations: none\n"
                    + "- Latency Expectations: none\n"
                    + "- Deployment Model: none\n"
                    + "- Operational Constraints: none\n"
                    + "- Runtime Topology: frontend=false, backend=false, worker=false\n"
                    + "- Stronger Model Warning: none";
        }

        final int minWorkstreams = profile.shapeEnvelope() == null ? 0 : profile.shapeEnvelope().minWorkstreamCount();
        final int maxWorkstreams = profile.shapeEnvelope() == null ? 0 : profile.shapeEnvelope().maxWorkstreamCount();
        final int minPhases = profile.shapeEnvelope() == null ? 0 : profile.shapeEnvelope().minPhaseCount();
        final int maxPhases = profile.shapeEnvelope() == null ? 0 : profile.shapeEnvelope().maxPhaseCount();
        final int minAgents = profile.shapeEnvelope() == null ? 0 : profile.shapeEnvelope().minAgentCount();
        final int maxAgents = profile.shapeEnvelope() == null ? 0 : profile.shapeEnvelope().maxAgentCount();
        final String outputBand = profile.shapeEnvelope() == null ? "none" : safeValue(profile.shapeEnvelope().outputBand());
        final String warning = profile.shapeEnvelope() == null
                ? "none"
                : blankToNone(profile.shapeEnvelope().strongerModelWarningReason());

        return """
                - Effective Complexity: %s
                - Hard Signals: %s
                - Mandatory Coverage Families: %s
                - Expected Workstreams: %d-%d
                - Expected Phases: %d-%d
                - Expected Agents: %d-%d
                - Output Band: %s
                - Scale Expectations: %s
                - Latency Expectations: %s
                - Deployment Model: %s
                - Operational Constraints: %s
                - Runtime Topology: frontend=%s, backend=%s, worker=%s
                - Stronger Model Warning: %s
                """.formatted(
                profile.effectiveComplexity(),
                joinValues(profile.hardSignals().stream().map(signal -> signal.value()).toList()),
                profile.shapeEnvelope() == null
                        ? "none"
                        : joinValues(profile.shapeEnvelope().requiredCoverageFamilies().stream().map(family -> family.value()).toList()),
                minWorkstreams,
                maxWorkstreams,
                minPhases,
                maxPhases,
                minAgents,
                maxAgents,
                blankToNone(outputBand),
                analysis != null ? safeValue(analysis.scaleExpectations()) : "none",
                analysis != null ? safeValue(analysis.latencyExpectations()) : "none",
                analysis != null ? safeValue(analysis.deploymentModel()) : "none",
                analysis != null ? joinValues(analysis.operationalConstraints()) : "none",
                analysis != null ? analysis.frontendNeededResolved() : false,
                analysis != null ? analysis.backendNeededResolved() : false,
                analysis != null ? analysis.workerNeededResolved() : false,
                warning);
    }

    private static String buildPassGuardrails() {
        return """
                - Treat the deterministic starter resolution as authoritative. Do not replace it with a different starter or incompatible stack.
                - Preserve the deterministic hard signals, mandatory coverage families, runtime topology, and expected shape envelope across this pass.
                - Use the expected shape envelope as planning bounds, not as an exact quota.
                """;
    }

    public static String architectureFramingPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final String ideTarget,
            final StarterSelection starterSelection,
            final boolean reducedMode) {
        return """
                Target IDE: %s
                Planning path: %s
                Deterministic Planning Profile:
%s

                Starter context:
                %s

                Authoritative planning rules:
                %s

                Canonical requirement packet:
                - Project name: %s
                - Project type: %s
                - Summary: %s
                - Tech stack: %s
                - Features: %s
                - Actors: %s
                - Business goals: %s
                - Delivery channels: %s
                - Deployment model: %s
                - Tenancy model: %s
                - Integrations: %s
                - Security requirements: %s
                - Assumptions: %s
                - Unknowns: %s
                - Open questions: %s
                - Unresolved constraints: %s
                - Complexity markers: %s

                Frame the architecture and identify the right workstreams before any detailed phase expansion.
                """.formatted(
                ideTarget,
                reducedMode ? "reduced_multi_pass" : "full_multi_pass",
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                safeValue(analysis.projectName()),
                safeValue(analysis.projectType()),
                safeValue(analysis.summary()),
                joinValues(analysis.techStack()),
                joinValues(analysis.features()),
                joinValues(analysis.actors()),
                joinValues(analysis.businessGoals()),
                joinValues(analysis.deliveryChannels()),
                safeValue(analysis.deploymentModel()),
                safeValue(analysis.tenancyModel()),
                joinValues(analysis.integrationPoints()),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.assumptions()),
                joinValues(analysis.unknowns()),
                joinValues(analysis.openQuestions()),
                joinValues(analysis.unresolvedConstraints()),
                joinValues(analysis.complexityMarkers()));
    }

    public static String workstreamDecompositionPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final ArchitectureFramingPass architectureFraming,
            final boolean reducedMode) {
        return """
                Planning path: %s
                Project summary: %s

                Deterministic Planning Profile:
%s

                Starter context:
                %s

                Authoritative planning rules:
                %s

                Architecture framing result:
                - Executive summary: %s
                - Architecture overview: %s
                - Workstream seeds: %s

                Requirement reminders:
                - Features: %s
                - Delivery channels: %s
                - Unknowns: %s
                - Constraints: %s

                Expand the workstreams into explicit scope, exclusions, dependencies, and suggested agents.
                """.formatted(
                reducedMode ? "reduced_multi_pass" : "full_multi_pass",
                safeValue(analysis.summary()),
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                safeValue(architectureFraming.executiveSummary()),
                safeValue(architectureFraming.architectureOverview()),
                describeWorkstreamSeeds(architectureFraming.workstreams()),
                joinValues(analysis.features()),
                joinValues(analysis.deliveryChannels()),
                joinValues(analysis.unknowns()),
                joinValues(analysis.unresolvedConstraints()));
    }

    public static String phaseExpansionPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final WorkstreamDecompositionPass workstreamDecomposition,
            final boolean reducedMode,
            final String expansionStrategyNotes) {
        return """
                Planning path: %s
                Project summary: %s

                Deterministic Planning Profile:
%s

                Starter context:
                %s

                Authoritative planning rules:
                %s

                Workstreams to expand:
                %s

                Requirement reminders:
                - Security requirements: %s
                - Data sources: %s
                - Integrations: %s
                - Assumptions: %s
                - Open questions: %s

                Expansion strategy:
                %s

                Expand each workstream into sequential phases with acceptance criteria, testing strategy, risks, and contracts.
                Reduced mode should still be implementable but may use fewer phases.
                """.formatted(
                reducedMode ? "reduced_multi_pass" : "full_multi_pass",
                safeValue(analysis.summary()),
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                describeWorkstreams(workstreamDecomposition.workstreams()),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.dataSources()),
                joinValues(analysis.integrationPoints()),
                joinValues(analysis.assumptions()),
                joinValues(analysis.openQuestions()),
                safeValue(expansionStrategyNotes));
    }

    public static String targetedPhaseExpansionPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final CanonicalBlueprint currentPlan,
            final String repairStrategyNotes) {
        return """
                Deterministic Planning Profile:
%s

                Starter context:
%s

                Authoritative planning rules:
                %s

                Existing blueprint summary:
                - Project: %s
                - Executive summary: %s
                - Architecture overview: %s
                - Workstreams with their current phases: %s

                Requirement reminders:
                - Security requirements: %s
                - Unknowns: %s
                - Constraints: %s

                Repair strategy:
                %s

                Repair goal:
                - return repaired workstreams with nested phases only
                - restore any missing workstream coverage so every workstream has at least one phase
                - preserve the architecture boundaries and workstream ordering when still valid
                - deepen weak phases with clearer contracts, testing, risks, security, and observability
                """.formatted(
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                safeValue(currentPlan.projectName()),
                safeValue(currentPlan.executiveSummary()),
                safeValue(currentPlan.architectureOverview()),
                describeWorkstreamsAndPhases(currentPlan.workstreams()),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.unknowns()),
                joinValues(analysis.unresolvedConstraints()),
                safeValue(repairStrategyNotes));
    }

    public static String targetedWorkstreamRestorationPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final CanonicalBlueprint currentPlan,
            final List<CanonicalWorkstream> missingWorkstreams,
            final String repairStrategyNotes) {
        return """
                Deterministic Planning Profile:
%s

                Starter context:
%s

                Authoritative planning rules:
                %s

                Existing blueprint summary:
                - Project: %s
                - Architecture overview: %s
                - Existing workstreams with phases: %s

                Restoration target workstreams:
                %s

                Requirement reminders:
                - Security requirements: %s
                - Unknowns: %s
                - Constraints: %s

                Repair strategy:
                %s

                Restoration goal:
                - return only the listed target workstreams with nested phases
                - restore missing workstream coverage without renaming or reordering the listed workstreams
                - each listed target workstream must receive at least one phase
                """.formatted(
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                safeValue(currentPlan.projectName()),
                safeValue(currentPlan.architectureOverview()),
                describeWorkstreamsAndPhases(currentPlan.workstreams()),
                describeWorkstreams(missingWorkstreams),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.unknowns()),
                joinValues(analysis.unresolvedConstraints()),
                safeValue(repairStrategyNotes));
    }

    public static String compactPhaseExpansionPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final WorkstreamDecompositionPass workstreamDecomposition,
            final boolean reducedMode,
            final String expansionStrategyNotes) {
        return """
                Recovery path: compact_phase_expansion
                Planning path: %s
                Project summary: %s

                Deterministic Planning Profile:
%s

                Starter context:
                %s

                Authoritative planning rules:
                %s

                Workstreams to expand:
                %s

                Requirement reminders:
                - Security requirements: %s
                - Integrations: %s
                - Assumptions: %s

                Expansion strategy:
                %s

                Recovery rules:
                - Preserve workstream titles, goals, dependencies, and order.
                - Return phases nested under each workstream.
                - Omit the top-level phases field.
                - Respect the expected phase range from the deterministic planning context instead of inventing a fixed cap.
                - Keep every text field to one short sentence.
                - Keep every list to at most 3 concise items.
                - Ensure each phase order is an integer.
                """.formatted(
                reducedMode ? "reduced_multi_pass" : "full_multi_pass",
                safeValue(analysis.summary()),
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                describeWorkstreams(workstreamDecomposition.workstreams()),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.integrationPoints()),
                joinValues(analysis.assumptions()),
                safeValue(expansionStrategyNotes));
    }

    public static String compactTargetedPhaseExpansionPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final CanonicalBlueprint currentPlan,
            final String repairStrategyNotes) {
        return """
                Recovery path: compact_targeted_phase_expansion
                Deterministic Planning Profile:
%s

                Starter context:
%s

                Authoritative planning rules:
                %s

                Existing blueprint summary:
                - Project: %s
                - Architecture overview: %s
                - Workstreams with their current phases: %s

                Requirement reminders:
                - Security requirements: %s
                - Unknowns: %s
                - Constraints: %s

                Repair strategy:
                %s

                Recovery rules:
                - Preserve the existing workstream titles and ordering.
                - Return only repaired workstreams with nested phases.
                - Omit the top-level phases field.
                - Respect the expected phase range from the deterministic planning context instead of inventing a fixed cap.
                - Keep every text field to one short sentence.
                - Keep every list to at most 3 concise items.
                - Ensure each phase order is an integer.
                """.formatted(
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                safeValue(currentPlan.projectName()),
                safeValue(currentPlan.architectureOverview()),
                describeWorkstreamsAndPhases(currentPlan.workstreams()),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.unknowns()),
                joinValues(analysis.unresolvedConstraints()),
                safeValue(repairStrategyNotes));
    }

    public static String compactTargetedWorkstreamRestorationPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final CanonicalBlueprint currentPlan,
            final List<CanonicalWorkstream> missingWorkstreams,
            final String repairStrategyNotes) {
        return """
                Recovery path: compact_targeted_workstream_restoration
                Deterministic Planning Profile:
%s

                Starter context:
%s

                Authoritative planning rules:
                %s

                Existing blueprint summary:
                - Project: %s
                - Architecture overview: %s
                - Existing workstreams with phases: %s

                Restoration target workstreams:
                %s

                Requirement reminders:
                - Security requirements: %s
                - Unknowns: %s
                - Constraints: %s

                Repair strategy:
                %s

                Recovery rules:
                - Return only the listed target workstreams with nested phases.
                - Do not rename, drop, or reorder the listed target workstreams.
                - Each listed target workstream must receive at least one phase.
                - Omit the top-level phases field.
                - Keep every text field to one short sentence.
                - Keep every list to at most 3 concise items.
                - Ensure each phase order is an integer.
                """.formatted(
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                safeValue(currentPlan.projectName()),
                safeValue(currentPlan.architectureOverview()),
                describeWorkstreamsAndPhases(currentPlan.workstreams()),
                describeWorkstreams(missingWorkstreams),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.unknowns()),
                joinValues(analysis.unresolvedConstraints()),
                safeValue(repairStrategyNotes));
    }

    public static String agentGenerationPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final StarterSelection starterSelection,
            final WorkstreamDecompositionPass workstreamDecomposition,
            final PhaseExpansionPass phaseExpansion,
            final boolean reducedMode) {
        return """
                Planning path: %s
                Project summary: %s

                Deterministic Planning Profile:
%s

                Starter context:
                %s

                Authoritative planning rules:
                %s

                Workstreams:
                %s

                Flattened phases:
                %s

                Generate only the specialized agents truly needed by this plan.
                """.formatted(
                reducedMode ? "reduced_multi_pass" : "full_multi_pass",
                safeValue(analysis.summary()),
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                describeWorkstreams(workstreamDecomposition.workstreams()),
                describePhases(flattenWorkstreamPhases(phaseExpansion.workstreams())));
    }

    public static String ideRulesPrompt(
            final DeterministicPlanningProfile profile,
            final StructuredAnalysis analysis,
            final String ideTarget,
            final StarterSelection starterSelection,
            final ArchitectureFramingPass architectureFraming,
            final PhaseExpansionPass phaseExpansion,
            final boolean reducedMode) {
        return """
                Target IDE: %s
                Planning path: %s
                Deterministic Planning Profile:
%s

                Starter context:
                %s

                Authoritative planning rules:
%s

                Architecture overview:
                %s

                Key phases:
                %s

                Requirement reminders:
                - Security requirements: %s
                - Compliance requirements: %s
                - Non-goals: %s
                - Constraints: %s

                Produce consistent IDE rules and starter-aware handoff notes.
                """.formatted(
                ideTarget,
                reducedMode ? "reduced_multi_pass" : "full_multi_pass",
                buildContextEnvelope(profile, analysis),
                buildStarterContext(starterSelection),
                buildPassGuardrails(),
                safeValue(architectureFraming.architectureOverview()),
                describePhases(flattenWorkstreamPhases(phaseExpansion.workstreams())),
                joinValues(analysis.securityRequirements()),
                joinValues(analysis.complianceRequirements()),
                joinValues(analysis.nonGoals()),
                joinValues(analysis.unresolvedConstraints()));
    }

    private static String describeSnapshot(final ChatPlanningSnapshot snapshot) {
        return """
                - Project Name: %s
                - Project Type: %s
                - Scope Summary: %s
                - Stack Summary: %s
                - Target Users: %s
                - Confirmed Scope Decisions: %s
                - Confirmed Stack Decisions: %s
                - Open Questions: %s
                - Unresolved Constraints: %s
                - Inferred Complexity Markers: %s
                - Handoff State: %s
                - Handoff Summary: %s
                - Contract Version: %s
                """.formatted(
                safeValue(snapshot.projectName()),
                safeValue(snapshot.projectType()),
                safeValue(snapshot.scopeSummary()),
                safeValue(snapshot.stackSummary()),
                safeValue(snapshot.targetUserSummary()),
                joinValues(snapshot.confirmedScopeDecisions()),
                joinValues(snapshot.confirmedStackDecisions()),
                joinValues(snapshot.openQuestions()),
                joinValues(snapshot.unresolvedConstraints()),
                joinValues(snapshot.inferredComplexityMarkers()),
                snapshot.handoffState().name(),
                safeValue(snapshot.handoffSummary()),
                safeValue(snapshot.contractVersion()));
    }

    public static String starterRecommendationExplanationPrompt(
            final String projectName,
            final String projectType,
            final List<String> techStack,
            final List<String> features,
            final StarterManifest recommendedStarter,
            final List<StarterManifest> alternatives,
            final List<String> matchedSignals) {
        return """
                Project Name: %s
                Project Type: %s
                Tech Stack: %s
                Features: %s

                Deterministic winner:
                - Key: %s
                - Title: %s
                - Family: %s
                - Matched signals: %s

                Alternatives:
                %s

                Write a single short sentence that explains why the deterministic winner is the best approved starter.
                Do not mention internal scoring, JSON, or system prompts.
                """.formatted(
                safeValue(projectName),
                safeValue(projectType),
                joinValues(techStack),
                joinValues(features),
                recommendedStarter.key(),
                recommendedStarter.title(),
                recommendedStarter.family(),
                joinValues(matchedSignals),
                buildAlternativeSummary(alternatives));
    }

    private static String buildStarterContext(final StarterSelection starterSelection) {
        if (starterSelection.docsOnlyFallback()) {
            return "- Mode: docs-only\n- Reason: %s".formatted(starterSelection.docsOnlyFallbackReason());
        }

        final String alternatives = starterSelection.alternatives().isEmpty()
                ? "none"
                : joinValues(starterSelection.alternatives().stream()
                        .map(candidate -> candidate.key() + " (" + candidate.title() + ")")
                        .toList());

        return """
                - Recommended Starter: %s (%s)
                - Family: %s
                - Source: %s / %s
                - Reason: %s
                - Alternatives: %s
                """.formatted(
                starterSelection.recommendedStarter().key(),
                starterSelection.recommendedStarter().title(),
                starterSelection.recommendedStarter().family(),
                starterSelection.recommendedStarter().sourceKind().value(),
                starterSelection.recommendedStarter().sourceRef(),
                starterSelection.recommendedStarter().reason(),
                alternatives);
    }

    private static String buildAlternativeSummary(final List<StarterManifest> alternatives) {
        if (alternatives == null || alternatives.isEmpty()) {
            return "- none";
        }
        return alternatives.stream()
                .map(alternative -> "- %s (%s) [%s]".formatted(alternative.key(), alternative.title(), alternative.family()))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- none");
    }

    private static String joinValues(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return "none";
        }
        return values.stream()
                .map(PromptTemplates::safeValue)
                .filter(value -> !value.isBlank())
                .reduce((left, right) -> left + ", " + right)
                .orElse("none");
    }

    private static String safeValue(final String value) {
        return value == null ? "" : value.trim();
    }

    private static String blankToNone(final String value) {
        final String normalizedValue = safeValue(value);
        return normalizedValue.isBlank() ? "none" : normalizedValue;
    }

    private static String describeWorkstreamSeeds(final List<WorkstreamSeed> workstreams) {
        if (workstreams == null || workstreams.isEmpty()) {
            return "- none";
        }
        return workstreams.stream()
                .map(workstream -> "- %s | goal: %s | why it exists: %s".formatted(
                        safeValue(workstream.title()),
                        safeValue(workstream.goal()),
                        safeValue(workstream.whyItExists())))
                .collect(Collectors.joining("\n"));
    }

    private static String describeWorkstreamsAndPhases(final List<CanonicalWorkstream> workstreams) {
        if (workstreams == null || workstreams.isEmpty()) {
            return "- none";
        }
        return workstreams.stream()
                .map(workstream -> {
                    String ws = "- %s | goal: %s | why it exists: %s | included scope: %s | excluded scope: %s | dependencies: %s | suggested agents: %s".formatted(
                            safeValue(workstream.title()),
                            safeValue(workstream.goal()),
                            safeValue(workstream.whyItExists()),
                            joinValues(workstream.includedScope()),
                            joinValues(workstream.excludedScope()),
                            joinValues(workstream.dependencies()),
                            joinValues(workstream.suggestedAgents()));
                    String ph = describePhases(workstream.phases());
                    return ws + "\n  Phases:\n" + ph.indent(4);
                })
                .collect(Collectors.joining("\n"));
    }

    private static String describeWorkstreams(final List<CanonicalWorkstream> workstreams) {
        if (workstreams == null || workstreams.isEmpty()) {
            return "- none";
        }
        return workstreams.stream()
                .map(workstream -> "- %s | goal: %s | why it exists: %s | included scope: %s | excluded scope: %s | dependencies: %s | suggested agents: %s".formatted(
                        safeValue(workstream.title()),
                        safeValue(workstream.goal()),
                        safeValue(workstream.whyItExists()),
                        joinValues(workstream.includedScope()),
                        joinValues(workstream.excludedScope()),
                        joinValues(workstream.dependencies()),
                        joinValues(workstream.suggestedAgents())))
                .collect(Collectors.joining("\n"));
    }

    private static List<CanonicalPhase> flattenWorkstreamPhases(final List<CanonicalWorkstream> workstreams) {
        if (workstreams == null || workstreams.isEmpty()) {
            return List.of();
        }
        return workstreams.stream()
                .flatMap(workstream -> workstream.phases().stream())
                .toList();
    }

    private static String describePhases(final List<CanonicalPhase> phases) {
        if (phases == null || phases.isEmpty()) {
            return "- none";
        }
        return phases.stream()
                .map(phase -> "- Phase %d: %s | objective: %s".formatted(
                        phase.order(),
                        safeValue(phase.title()),
                        safeValue(phase.objective())))
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- none");
    }
}
