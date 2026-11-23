package com.arslanca.orchestrai.business.concretes;

import java.util.ArrayList;
import java.time.OffsetDateTime;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.function.Function;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.abstracts.MultiPassBlueprintPlannerService;
import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.abstracts.PlanningPassValidationService;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningCoverageFamily;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.AgentGenerationPass;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.ArchitectureFramingPass;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.IdeRulesPass;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.PhaseExpansionPass;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.WorkstreamDecompositionPass;
import com.arslanca.orchestrai.business.dtos.orchestration.MultiPassPlanningArtifacts.WorkstreamSeed;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalAgent;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalPhase;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalWorkstream;
import com.arslanca.orchestrai.business.prompt.PromptTemplates;
import com.arslanca.orchestrai.business.prompt.SystemPrompts;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;
import com.arslanca.orchestrai.core.exception.type.LlmResponseValidationException;
import com.arslanca.orchestrai.core.util.LlmResponseSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;

@Service
public class MultiPassBlueprintPlannerManager implements MultiPassBlueprintPlannerService {

  private static final Logger log = LoggerFactory.getLogger(MultiPassBlueprintPlannerManager.class);
  private static final int MAX_PASS_PARSE_ATTEMPTS = 2;
  private static final int DEFAULT_PHASE_EXPANSION_BATCH_SIZE = 2;
  private static final int BATCH_PHASE_EXPANSION_WORKSTREAM_THRESHOLD = 4;
  private static final String JSON_RETRY_SUFFIX = """

      The previous response could not be parsed as complete JSON.
      Re-emit the full answer as exactly one valid JSON object.
      Keep long markdown fields concise so the response remains complete.
      Do not use code fences, commentary, or surrounding prose.
      """;

  private final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;
  private final ObjectMapper objectMapper;
  private final PlanningTelemetryService planningTelemetryService;
  private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;
  private final PlanningPassValidationService planningPassValidationService;
  private final AiUsageCaptureService aiUsageCaptureService;

  @Autowired
  public MultiPassBlueprintPlannerManager(
      final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager,
      final ObjectMapper objectMapper,
      final PlanningTelemetryService planningTelemetryService,
      final DeterministicPlanningPolicyService deterministicPlanningPolicyService,
      final PlanningPassValidationService planningPassValidationService,
      final AiUsageCaptureService aiUsageCaptureService) {
    this.resolvedHeavyModelRuntimeBindingManager = resolvedHeavyModelRuntimeBindingManager;
    this.objectMapper = objectMapper;
    this.planningTelemetryService = planningTelemetryService;
    this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
    this.planningPassValidationService = planningPassValidationService;
    this.aiUsageCaptureService = aiUsageCaptureService;
  }

  MultiPassBlueprintPlannerManager(
      final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager,
      final ObjectMapper objectMapper,
      final PlanningTelemetryService planningTelemetryService,
      final DeterministicPlanningPolicyService deterministicPlanningPolicyService,
      final PlanningPassValidationService planningPassValidationService) {
    this(
        resolvedHeavyModelRuntimeBindingManager,
        objectMapper,
        planningTelemetryService,
        deterministicPlanningPolicyService,
        planningPassValidationService,
        null);
  }

  @Override
  public CanonicalBlueprint generateBlueprint(
      final StructuredAnalysis analysis,
      final String ideTarget,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final boolean reducedMode) {
    return generateBlueprint(
        analysis,
        ideTarget,
        resolvedHeavyModel,
        starterSelection,
        reducedMode,
        GenerationUsageContext.empty());
  }

  @Override
  public CanonicalBlueprint generateBlueprint(
      final StructuredAnalysis analysis,
      final String ideTarget,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final boolean reducedMode,
      final GenerationUsageContext generationUsageContext) {
    log.info("Multi-pass blueprint planning starting - project={}, ide={}, heavyModel={}, reducedMode={}",
        analysis.projectName(), ideTarget, resolvedHeavyModel.modelKey(), reducedMode);

    final DeterministicPlanningProfile profile = deterministicPlanningPolicyService.deriveProfile(analysis);
    final PlanningComplexity planningComplexity = analysis.planningComplexity();
    final PlannerModelRef plannerModelRef = PlannerModelRef.fromResolvedHeavyModel(resolvedHeavyModel);
    if (shouldUseDeterministicSkeletonBlueprint(plannerModelRef, reducedMode)) {
      log.info(
          "Using deterministic skeleton blueprint path for reduced local planner lane - project={}, heavyModel={}",
          analysis.projectName(),
          plannerModelRef == null ? "unknown" : plannerModelRef.resolvedModelKey());
      return synthesizeDeterministicSkeletonBlueprint(
          profile,
          analysis,
          ideTarget,
          starterSelection,
          plannerModelRef,
          reducedMode);
    }

    final ResolvedAiExecutionRoute executionRoute = buildExecutionRoute(resolvedHeavyModel);
    final ChatModel heavyChatModel = executionRoute.chatModel();
    final ArchitectureFramingPass architectureFraming = callPass(
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        SystemPrompts.ARCHITECTURE_FRAMER,
        PromptTemplates.architectureFramingPrompt(profile, analysis, ideTarget, starterSelection, reducedMode),
        ArchitectureFramingPass.class,
        "architecture framing");
    final WorkstreamDecompositionPass workstreamDecomposition = callWorkstreamDecompositionPass(
        profile,
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        analysis,
        starterSelection,
        architectureFraming,
        reducedMode);
    final PhaseExpansionPass phaseExpansion = callPhaseExpansionPass(
        profile,
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        analysis,
        starterSelection,
        workstreamDecomposition,
        reducedMode);
    final AgentGenerationPass agentGeneration = callPass(
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        SystemPrompts.AGENT_GENERATOR,
        PromptTemplates.agentGenerationPrompt(profile, analysis, starterSelection, workstreamDecomposition, phaseExpansion,
            reducedMode),
        AgentGenerationPass.class,
        "agent generation");
    final IdeRulesPass ideRules = callPass(
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        SystemPrompts.IDE_RULES_SYNTHESIZER,
        PromptTemplates.ideRulesPrompt(profile, analysis, ideTarget, starterSelection, architectureFraming, phaseExpansion,
            reducedMode),
        IdeRulesPass.class,
        "ide rules synthesis");

    final List<CanonicalWorkstream> workstreams = phaseExpansion.workstreams().isEmpty()
        ? workstreamDecomposition.workstreams()
        : phaseExpansion.workstreams();
    final List<CanonicalPhase> phases = flattenWorkstreamPhases(workstreams);

    final CanonicalBlueprint canonical = new CanonicalBlueprint(
        architectureFraming.projectName().isBlank() ? analysis.projectName() : architectureFraming.projectName(),
        architectureFraming.executiveSummary(),
        architectureFraming.architectureOverview(),
        architectureFraming.systemContext(),
        architectureFraming.majorArchitectureDecisions(),
        workstreams,
        architectureFraming.nonFunctionalRequirements(),
        architectureFraming.crossCuttingConcerns(),
        architectureFraming.glossary(),
        analysis.assumptions(),
        analysis.unknowns(),
        phases,
        agentGeneration.agents(),
        ideRules.ideRules(),
        mergeStarterNotes(ideRules.starterNotes(), starterSelection),
        CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
    return canonical;
  }

  private boolean shouldUseDeterministicSkeletonBlueprint(
      final PlannerModelRef plannerModelRef,
      final boolean reducedMode) {
    return shouldUseDeterministicWorkstreamDecomposition(plannerModelRef, reducedMode);
  }

  private CanonicalBlueprint synthesizeDeterministicSkeletonBlueprint(
      final DeterministicPlanningProfile profile,
      final StructuredAnalysis analysis,
      final String ideTarget,
      final StarterSelection starterSelection,
      final PlannerModelRef plannerModelRef,
      final boolean reducedMode) {
    final WorkstreamDecompositionPass workstreamDecomposition = synthesizeDeterministicWorkstreamDecomposition(
        "deterministic skeleton blueprint",
        profile,
        analysis,
        null);
    final int requiredPhaseFloor = requiredPhaseFloor(
        profile,
        workstreamDecomposition.workstreams().size(),
        plannerModelRef,
        reducedMode);
    final PhaseExpansionPass phaseExpansion = synthesizeDeterministicPhaseExpansion(
        analysis,
        workstreamDecomposition.workstreams(),
        requiredPhaseFloor);
    final List<CanonicalWorkstream> workstreams = phaseExpansion.workstreams();
    final List<CanonicalPhase> phases = flattenWorkstreamPhases(workstreams);
    final List<CanonicalAgent> agents = synthesizeDeterministicAgents(profile, analysis, workstreams);

    return new CanonicalBlueprint(
        resolvedProjectName(analysis),
        synthesizeExecutiveSummary(analysis, workstreams),
        synthesizeArchitectureOverview(analysis, starterSelection, workstreams),
        synthesizeSystemContext(analysis, workstreams),
        synthesizeMajorArchitectureDecisions(analysis, starterSelection),
        workstreams,
        synthesizeNonFunctionalRequirements(analysis),
        synthesizeCrossCuttingConcerns(analysis),
        synthesizeGlossary(analysis, workstreams),
        analysis.assumptions(),
        analysis.unknowns(),
        phases,
        agents,
        synthesizeDeterministicIdeRules(analysis, ideTarget, starterSelection),
        mergeStarterNotes("", starterSelection),
        CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
  }

  @Override
  public CanonicalBlueprint expandBlueprintDepth(
      final StructuredAnalysis analysis,
      final String ideTarget,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final CanonicalBlueprint currentPlan) {
    return expandBlueprintDepth(
        analysis,
        ideTarget,
        resolvedHeavyModel,
        starterSelection,
        currentPlan,
        GenerationUsageContext.empty());
  }

  @Override
  public CanonicalBlueprint expandBlueprintDepth(
      final StructuredAnalysis analysis,
      final String ideTarget,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final CanonicalBlueprint currentPlan,
      final GenerationUsageContext generationUsageContext) {
    log.info("Targeted blueprint expansion starting - project={}, ide={}, heavyModel={}",
        analysis.projectName(), ideTarget, resolvedHeavyModel.modelKey());

    final ResolvedAiExecutionRoute executionRoute = buildExecutionRoute(resolvedHeavyModel);
    final ChatModel heavyChatModel = executionRoute.chatModel();
    final DeterministicPlanningProfile profile = deterministicPlanningPolicyService.deriveProfile(analysis);
    final PlanningComplexity planningComplexity = analysis.planningComplexity();
    final PlannerModelRef plannerModelRef = PlannerModelRef.fromResolvedHeavyModel(resolvedHeavyModel);
    final PhaseExpansionPass phaseExpansion = callTargetedPhaseExpansionPass(
        profile,
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        analysis,
        starterSelection,
        currentPlan);

    final List<CanonicalWorkstream> workstreams = phaseExpansion.workstreams().isEmpty()
        ? currentPlan.workstreams()
        : phaseExpansion.workstreams();
    final List<CanonicalPhase> phases = flattenWorkstreamPhases(workstreams);

    final WorkstreamDecompositionPass decompositionPass = new WorkstreamDecompositionPass(workstreams);
    final AgentGenerationPass agentGeneration = callPass(
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        SystemPrompts.AGENT_GENERATOR,
        PromptTemplates.agentGenerationPrompt(profile, analysis, starterSelection, decompositionPass, phaseExpansion, false),
        AgentGenerationPass.class,
        "agent regeneration");
    final IdeRulesPass ideRules = callPass(
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        SystemPrompts.IDE_RULES_SYNTHESIZER,
        PromptTemplates.ideRulesPrompt(
            profile,
            analysis,
            ideTarget,
            starterSelection,
            toArchitectureFraming(currentPlan),
            phaseExpansion,
            false),
        IdeRulesPass.class,
        "ide rules repair");

    final CanonicalBlueprint canonical = new CanonicalBlueprint(
        currentPlan.projectName(),
        currentPlan.executiveSummary(),
        currentPlan.architectureOverview(),
        currentPlan.systemContext(),
        currentPlan.majorArchitectureDecisions(),
        workstreams,
        currentPlan.nonFunctionalRequirements(),
        currentPlan.crossCuttingConcerns(),
        currentPlan.glossary(),
        currentPlan.assumptions().isEmpty() ? analysis.assumptions() : currentPlan.assumptions(),
        currentPlan.unknowns().isEmpty() ? analysis.unknowns() : currentPlan.unknowns(),
        phases,
        fallbackAgents(currentPlan, agentGeneration),
        ideRules.ideRules().isBlank() ? currentPlan.ideRules() : ideRules.ideRules(),
        mergeStarterNotes(ideRules.starterNotes(), starterSelection),
        CanonicalBlueprint.CURRENT_CONTRACT_VERSION);
    return canonical;
  }

  private ResolvedAiExecutionRoute buildExecutionRoute(final ResolvedHeavyModel resolvedHeavyModel) {
    final var runtimeBinding = resolvedHeavyModelRuntimeBindingManager.bind(resolvedHeavyModel);
    final ChatModel chatModel = runtimeBinding.buildChatModel();
    return runtimeBinding.toExecutionRoute(resolvedHeavyModel.executionClass(), chatModel);
  }

  private ChatModel buildChatModel(final ResolvedHeavyModel resolvedHeavyModel) {
    return buildExecutionRoute(resolvedHeavyModel).chatModel();
  }

  private WorkstreamDecompositionPass callWorkstreamDecompositionPass(
      final DeterministicPlanningProfile profile,
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final StructuredAnalysis analysis,
      final StarterSelection starterSelection,
      final ArchitectureFramingPass architectureFraming,
      final boolean reducedMode) {
    if (shouldUseDeterministicWorkstreamDecomposition(plannerModelRef, reducedMode)) {
      log.info(
          "Using deterministic workstream decomposition for reduced local planner path - project={}, heavyModel={}",
          analysis.projectName(),
          plannerModelRef == null ? "unknown" : plannerModelRef.resolvedModelKey());
      return synthesizeDeterministicWorkstreamDecomposition(
          "workstream decomposition deterministic path",
          profile,
          analysis,
          architectureFraming);
    }

    try {
      return validateWorkstreamDecompositionPass(
          "workstream decomposition",
          callPass(
              planningComplexity,
              plannerModelRef,
              executionRoute,
              generationUsageContext,
              heavyChatModel,
              SystemPrompts.WORKSTREAM_DECOMPOSER,
              PromptTemplates.workstreamDecompositionPrompt(profile, analysis, starterSelection, architectureFraming, reducedMode),
              WorkstreamDecompositionPass.class,
              "workstream decomposition"));
    } catch (final LlmResponseParseException exception) {
      return recoverDeterministicWorkstreamDecomposition(profile, analysis, architectureFraming, exception);
    }
  }

  private WorkstreamDecompositionPass recoverDeterministicWorkstreamDecomposition(
      final DeterministicPlanningProfile profile,
      final StructuredAnalysis analysis,
      final ArchitectureFramingPass architectureFraming,
      final LlmResponseParseException originalException) {
    log.warn(
        "Falling back to deterministic workstream decomposition after parse/validation failure: reason={}",
        originalException.getMessage());

    try {
      return synthesizeDeterministicWorkstreamDecomposition(
          "workstream decomposition deterministic recovery",
          profile,
          analysis,
          architectureFraming);
    } catch (final LlmResponseParseException recoveryException) {
      recoveryException.addSuppressed(originalException);
      throw recoveryException;
    }
  }

  private boolean shouldUseDeterministicWorkstreamDecomposition(
      final PlannerModelRef plannerModelRef,
      final boolean reducedMode) {
    if (!reducedMode || plannerModelRef == null) {
      return false;
    }
    return plannerModelRef.localModel() && plannerModelRef.modelCapability() != ModelCapability.HIGH;
  }

  private WorkstreamDecompositionPass synthesizeDeterministicWorkstreamDecomposition(
      final String passName,
      final DeterministicPlanningProfile profile,
      final StructuredAnalysis analysis,
      final ArchitectureFramingPass architectureFraming) {
    final List<WorkstreamSeed> recoverySeeds = selectDeterministicWorkstreamSeeds(profile, analysis, architectureFraming);
    final List<CanonicalWorkstream> synthesizedWorkstreams = buildDeterministicWorkstreams(recoverySeeds, analysis);
    return validateWorkstreamDecompositionPass(passName, new WorkstreamDecompositionPass(synthesizedWorkstreams));
  }

  private PhaseExpansionPass synthesizeDeterministicPhaseExpansion(
      final StructuredAnalysis analysis,
      final List<CanonicalWorkstream> expectedWorkstreams,
      final int requiredPhaseFloor) {
    if (expectedWorkstreams == null || expectedWorkstreams.isEmpty()) {
      return new PhaseExpansionPass(List.of());
    }

    final int normalizedPhaseFloor = Math.max(requiredPhaseFloor, expectedWorkstreams.size());
    final int basePhaseCount = normalizedPhaseFloor / expectedWorkstreams.size();
    final int remainder = normalizedPhaseFloor % expectedWorkstreams.size();
    final List<CanonicalWorkstream> expandedWorkstreams = new ArrayList<>(expectedWorkstreams.size());

    for (int workstreamIndex = 0; workstreamIndex < expectedWorkstreams.size(); workstreamIndex++) {
      final CanonicalWorkstream workstream = expectedWorkstreams.get(workstreamIndex);
      final int phaseCount = Math.max(1, basePhaseCount + (workstreamIndex < remainder ? 1 : 0));
      expandedWorkstreams.add(new CanonicalWorkstream(
          workstream.title(),
          workstream.goal(),
          workstream.whyItExists(),
          workstream.includedScope(),
          workstream.excludedScope(),
          workstream.dependencies(),
          workstream.suggestedAgents(),
          synthesizeDeterministicPhasesForWorkstream(workstream, analysis, phaseCount)));
    }

    return normalizePhaseExpansionPass(new PhaseExpansionPass(expandedWorkstreams), expectedWorkstreams);
  }

  private List<WorkstreamSeed> selectDeterministicWorkstreamSeeds(
      final DeterministicPlanningProfile profile,
      final StructuredAnalysis analysis,
      final ArchitectureFramingPass architectureFraming) {
    final Map<String, WorkstreamSeed> seedsByTitle = new LinkedHashMap<>();
    if (architectureFraming != null && architectureFraming.workstreams() != null) {
      for (final WorkstreamSeed seed : architectureFraming.workstreams()) {
        if (seed == null || seed.title().isBlank()) {
          continue;
        }
        seedsByTitle.putIfAbsent(normalizeWorkstreamKey(seed.title()), seed);
      }
    }

    if (seedsByTitle.isEmpty()) {
      final List<PlanningCoverageFamily> coverageFamilies = profile == null || profile.shapeEnvelope() == null
          ? List.of()
          : profile.shapeEnvelope().requiredCoverageFamilies();
      for (final PlanningCoverageFamily coverageFamily : coverageFamilies) {
        final WorkstreamSeed synthesizedSeed = seedForCoverageFamily(coverageFamily);
        if (synthesizedSeed != null && !synthesizedSeed.title().isBlank()) {
          seedsByTitle.putIfAbsent(normalizeWorkstreamKey(synthesizedSeed.title()), synthesizedSeed);
        }
      }
    }

    if (seedsByTitle.isEmpty()) {
      addRuntimeFallbackSeed(seedsByTitle, analysis.backendNeededResolved(), new WorkstreamSeed(
          "Backend Foundation",
          "Establish the core backend delivery path.",
          "The project needs a stable backend execution boundary."));
      addRuntimeFallbackSeed(seedsByTitle, analysis.workerNeededResolved(), new WorkstreamSeed(
          "Async Processing",
          "Handle background and event-driven execution.",
          "The runtime shape includes asynchronous or worker concerns."));
      addRuntimeFallbackSeed(seedsByTitle, analysis.frontendNeededResolved(), new WorkstreamSeed(
          "Frontend Experience",
          "Deliver the interactive user-facing surface.",
          "The runtime shape includes a frontend delivery surface."));
    }

    if (seedsByTitle.isEmpty()) {
      seedsByTitle.put(
          normalizeWorkstreamKey("Core Delivery"),
          new WorkstreamSeed(
              "Core Delivery",
              "Turn the approved architecture into an executable plan.",
              "A fallback execution workstream is required when the planner omits workstream seeds."));
    }
    return List.copyOf(seedsByTitle.values());
  }

  private void addRuntimeFallbackSeed(
      final Map<String, WorkstreamSeed> seedsByTitle,
      final boolean shouldAdd,
      final WorkstreamSeed seed) {
    if (!shouldAdd || seed == null || seed.title().isBlank()) {
      return;
    }
    seedsByTitle.putIfAbsent(normalizeWorkstreamKey(seed.title()), seed);
  }

  private WorkstreamSeed seedForCoverageFamily(final PlanningCoverageFamily coverageFamily) {
    if (coverageFamily == null) {
      return null;
    }
    return switch (coverageFamily) {
      case FRONTEND_EXPERIENCE -> new WorkstreamSeed(
          "Frontend Experience",
          "Deliver the user-facing interface and navigation flows.",
          "This workstream owns the client experience described by the planning profile.");
      case BACKEND_FOUNDATION -> new WorkstreamSeed(
          "Backend Foundation",
          "Deliver the core API, domain, and service backbone.",
          "This workstream owns the primary backend execution path.");
      case ASYNC_PROCESSING -> new WorkstreamSeed(
          "Async Processing",
          "Deliver worker, event, and background processing capabilities.",
          "This workstream owns asynchronous execution boundaries.");
      case IDENTITY_AND_ACCESS -> new WorkstreamSeed(
          "Identity And Access",
          "Deliver authentication, authorization, and access controls.",
          "This workstream owns the security boundary around user and system access.");
      case TENANCY_AND_ISOLATION -> new WorkstreamSeed(
          "Tenancy And Isolation",
          "Deliver tenant-aware data and execution isolation.",
          "This workstream owns organization and tenant boundaries.");
      case OPERATIONS_AND_DEPLOYMENT -> new WorkstreamSeed(
          "Operations And Deployment",
          "Deliver observability, deployment, and operational readiness.",
          "This workstream owns production operations and rollout concerns.");
      case DATA_AND_STORAGE -> new WorkstreamSeed(
          "Data And Storage",
          "Deliver persistence, schema, and data lifecycle coverage.",
          "This workstream owns durable data and storage boundaries.");
      case INTEGRATIONS -> new WorkstreamSeed(
          "External Integrations",
          "Deliver third-party and provider integration boundaries.",
          "This workstream owns external system touchpoints.");
      case ANALYTICS_AND_QUERY -> new WorkstreamSeed(
          "Analytics And Query",
          "Deliver reporting, insights, and query-serving capabilities.",
          "This workstream owns analytical read and aggregation flows.");
      case UNKNOWN -> null;
    };
  }

  private List<CanonicalWorkstream> buildDeterministicWorkstreams(
      final List<WorkstreamSeed> workstreamSeeds,
      final StructuredAnalysis analysis) {
    final List<CanonicalWorkstream> synthesizedWorkstreams = new ArrayList<>();
    final List<String> priorTitles = new ArrayList<>();

    for (final WorkstreamSeed workstreamSeed : workstreamSeeds) {
      if (workstreamSeed == null || workstreamSeed.title().isBlank()) {
        continue;
      }
      final List<PlanningCoverageFamily> matchedFamilies = matchedCoverageFamilies(workstreamSeed);
      synthesizedWorkstreams.add(new CanonicalWorkstream(
          workstreamSeed.title(),
          workstreamSeed.goal().isBlank() ? "Deliver %s scope.".formatted(workstreamSeed.title()) : workstreamSeed.goal(),
          workstreamSeed.whyItExists().isBlank()
              ? "This workstream preserves the approved architecture boundary for %s.".formatted(workstreamSeed.title())
              : workstreamSeed.whyItExists(),
          synthesizeIncludedScope(workstreamSeed, analysis),
          synthesizeExcludedScope(analysis),
          synthesizeDependencies(matchedFamilies, priorTitles),
          synthesizeSuggestedAgents(matchedFamilies, workstreamSeed, analysis),
          List.of()));
      priorTitles.add(workstreamSeed.title());
    }
    return List.copyOf(synthesizedWorkstreams);
  }

  private List<PlanningCoverageFamily> matchedCoverageFamilies(final WorkstreamSeed workstreamSeed) {
    final String corpus = String.join(" ", workstreamSeed.title(), workstreamSeed.goal(), workstreamSeed.whyItExists());
    return PlanningCoverageFamily.matchedBy(corpus);
  }

  private List<String> synthesizeIncludedScope(
      final WorkstreamSeed workstreamSeed,
      final StructuredAnalysis analysis) {
    final String corpus = String.join(" ", workstreamSeed.title(), workstreamSeed.goal(), workstreamSeed.whyItExists());
    final LinkedHashSet<String> includedScope = new LinkedHashSet<>();
    collectRelevantItems(includedScope, analysis.features(), corpus, 3);
    collectRelevantItems(includedScope, analysis.integrationPoints(), corpus, 3);
    collectRelevantItems(includedScope, analysis.dataSources(), corpus, 3);
    collectRelevantItems(includedScope, analysis.securityRequirements(), corpus, 3);
    if (includedScope.isEmpty()) {
      collectFallbackItems(includedScope, analysis.features(), 2);
    }
    if (includedScope.isEmpty()) {
      collectFallbackItems(includedScope, analysis.businessGoals(), 2);
    }
    return List.copyOf(includedScope);
  }

  private void collectRelevantItems(
      final LinkedHashSet<String> target,
      final List<String> candidates,
      final String corpus,
      final int maxItems) {
    if (candidates == null || candidates.isEmpty() || target.size() >= maxItems) {
      return;
    }
    final String normalizedCorpus = corpus == null ? "" : corpus.toLowerCase(Locale.ROOT);
    final List<String> corpusTokens = List.of(normalizedCorpus.split("[^a-z0-9]+"));
    final List<PlanningCoverageFamily> matchedFamilies = PlanningCoverageFamily.matchedBy(corpus);
    for (final String candidate : candidates) {
      if (candidate == null || candidate.isBlank() || target.size() >= maxItems) {
        continue;
      }
      final String normalizedCandidate = candidate.toLowerCase(Locale.ROOT);
      final boolean matchesToken = corpusTokens.stream()
          .filter(token -> token.length() > 2)
          .anyMatch(normalizedCandidate::contains);
      final boolean matchesCoverage = matchedFamilies.stream().anyMatch(family -> family.matches(candidate));
      if (matchesToken || matchesCoverage) {
        target.add(candidate.trim());
      }
    }
  }

  private void collectFallbackItems(
      final LinkedHashSet<String> target,
      final List<String> candidates,
      final int maxItems) {
    if (candidates == null || candidates.isEmpty() || target.size() >= maxItems) {
      return;
    }
    for (final String candidate : candidates) {
      if (candidate == null || candidate.isBlank() || target.size() >= maxItems) {
        continue;
      }
      target.add(candidate.trim());
    }
  }

  private List<String> synthesizeExcludedScope(final StructuredAnalysis analysis) {
    final LinkedHashSet<String> excludedScope = new LinkedHashSet<>();
    collectFallbackItems(excludedScope, analysis.nonGoals(), 2);
    return List.copyOf(excludedScope);
  }

  private List<String> synthesizeDependencies(
      final List<PlanningCoverageFamily> matchedFamilies,
      final List<String> priorTitles) {
    if (priorTitles.isEmpty()) {
      return List.of();
    }
    if (matchedFamilies.contains(PlanningCoverageFamily.FRONTEND_EXPERIENCE)
        || matchedFamilies.contains(PlanningCoverageFamily.ANALYTICS_AND_QUERY)
        || matchedFamilies.contains(PlanningCoverageFamily.IDENTITY_AND_ACCESS)
        || matchedFamilies.contains(PlanningCoverageFamily.TENANCY_AND_ISOLATION)
        || matchedFamilies.contains(PlanningCoverageFamily.OPERATIONS_AND_DEPLOYMENT)) {
      return List.of(priorTitles.get(priorTitles.size() - 1));
    }
    return List.of();
  }

  private List<String> synthesizeSuggestedAgents(
      final List<PlanningCoverageFamily> matchedFamilies,
      final WorkstreamSeed workstreamSeed,
      final StructuredAnalysis analysis) {
    final LinkedHashSet<String> suggestedAgents = new LinkedHashSet<>();
    if (matchedFamilies.contains(PlanningCoverageFamily.FRONTEND_EXPERIENCE)) {
      suggestedAgents.add("frontend-engineer");
    }
    if (matchedFamilies.stream().anyMatch(family -> family == PlanningCoverageFamily.BACKEND_FOUNDATION
        || family == PlanningCoverageFamily.ASYNC_PROCESSING
        || family == PlanningCoverageFamily.IDENTITY_AND_ACCESS
        || family == PlanningCoverageFamily.TENANCY_AND_ISOLATION
        || family == PlanningCoverageFamily.DATA_AND_STORAGE
        || family == PlanningCoverageFamily.INTEGRATIONS
        || family == PlanningCoverageFamily.ANALYTICS_AND_QUERY
        || family == PlanningCoverageFamily.OPERATIONS_AND_DEPLOYMENT)) {
      suggestedAgents.add("backend-engineer");
    }
    if (suggestedAgents.isEmpty()) {
      final String normalizedTitle = workstreamSeed.title().toLowerCase(Locale.ROOT);
      if (analysis.frontendNeededResolved()
          && (normalizedTitle.contains("frontend") || normalizedTitle.contains("dashboard") || normalizedTitle.contains("ui"))) {
        suggestedAgents.add("frontend-engineer");
      } else {
        suggestedAgents.add("backend-engineer");
      }
    }
    return List.copyOf(suggestedAgents);
  }

  private List<CanonicalPhase> synthesizeDeterministicPhasesForWorkstream(
      final CanonicalWorkstream workstream,
      final StructuredAnalysis analysis,
      final int phaseCount) {
    final List<CanonicalPhase> phases = new ArrayList<>(phaseCount);
    final String workstreamTitle = workstream == null || workstream.title().isBlank() ? "workstream" : workstream.title();
    final String workstreamGoal = workstream == null || workstream.goal().isBlank()
        ? "deliver the approved scope"
        : workstream.goal();
    final List<String> includedScope = workstream == null ? List.of() : workstream.includedScope();
    final String primaryScope = firstNonBlank(includedScope, workstreamGoal, workstreamTitle);
    final String primaryDependency = firstNonBlank(workstream == null ? List.of() : workstream.dependencies());
    final String securityFocus = firstNonBlank(
        selectRelevantItems(analysis.securityRequirements(), workstreamTitle, 1),
        "least-privilege access",
        "security hardening");

    for (int phaseIndex = 0; phaseIndex < phaseCount; phaseIndex++) {
      final String phaseLabel = phaseLabelFor(phaseIndex, phaseCount);
      final String phaseTitle = phaseTitleFor(phaseLabel, workstreamTitle, primaryScope);
      final String dependencyText = phaseIndex == 0
          ? (primaryDependency == null ? "the approved preparation context" : primaryDependency)
          : phases.get(phaseIndex - 1).title();

      phases.add(new CanonicalPhase(
          phaseIndex + 1,
          phaseTitle,
          "%s the %s execution slice around %s.".formatted(phaseLabel, workstreamTitle, primaryScope),
          "This phase exists so the %s boundary can progress without breaking adjacent workstreams.".formatted(workstreamTitle),
          List.of(primaryScope),
          workstream == null ? List.of() : workstream.excludedScope(),
          List.of(dependencyText),
          List.of("%s deliverables for %s".formatted(phaseLabel, workstreamTitle), "%s contract".formatted(primaryScope)),
          List.of("Preserve the %s boundary while sequencing incremental delivery.".formatted(workstreamTitle)),
          List.of("%s interface contract".formatted(primaryScope)),
          "Update the data and state transitions required by %s.".formatted(workstreamTitle),
          List.of("Apply %s controls to %s.".formatted(securityFocus, workstreamTitle)),
          "Track latency, failures, and rollout signals for %s.".formatted(workstreamTitle),
          "Run contract, integration, and regression coverage for %s.".formatted(workstreamTitle),
          List.of("%s moves forward with verified contracts.".formatted(workstreamTitle),
              "%s remains compatible with the approved starter and surrounding phases.".formatted(workstreamTitle)),
          List.of("Scope drift across %s".formatted(workstreamTitle), "Operational regressions in %s".formatted(primaryScope)),
          "Proceed to the next %s slice only after tests, telemetry, and contracts are stable.".formatted(workstreamTitle)));
    }

    return List.copyOf(phases);
  }

  private String phaseLabelFor(final int phaseIndex, final int phaseCount) {
    if (phaseCount == 1) {
      return "Deliver";
    }

    return switch (phaseIndex) {
      case 0 -> "Define";
      case 1 -> "Implement";
      case 2 -> "Harden";
      case 3 -> "Operationalize";
      case 4 -> "Expand";
      default -> "Validate";
    };
  }

  private String phaseTitleFor(
      final String phaseLabel,
      final String workstreamTitle,
      final String primaryScope) {
    return switch (phaseLabel) {
      case "Define" -> "%s %s contracts".formatted(phaseLabel, workstreamTitle);
      case "Implement" -> "%s %s delivery".formatted(phaseLabel, workstreamTitle);
      case "Harden" -> "%s %s quality".formatted(phaseLabel, workstreamTitle);
      case "Operationalize" -> "%s %s rollout".formatted(phaseLabel, workstreamTitle);
      case "Expand" -> "%s %s coverage".formatted(phaseLabel, workstreamTitle);
      case "Validate" -> "%s %s readiness".formatted(phaseLabel, workstreamTitle);
      default -> "%s %s around %s".formatted(phaseLabel, workstreamTitle, primaryScope);
    };
  }

  private List<CanonicalAgent> synthesizeDeterministicAgents(
      final DeterministicPlanningProfile profile,
      final StructuredAnalysis analysis,
      final List<CanonicalWorkstream> workstreams) {
    final LinkedHashMap<String, CanonicalAgent> agentsByRole = new LinkedHashMap<>();
    appendDeterministicAgent(agentsByRole, new CanonicalAgent(
        "System Architect",
        "Preserve the approved workstream boundaries, sequencing, and starter alignment across every phase.",
        List.of("Guard architecture decisions", "Review phase handoffs", "Protect starter compatibility")));

    final List<PlanningCoverageFamily> representedFamilies = PlanningCoverageFamily.matchedBy(
        buildDeterministicCoverageCorpus(workstreams));
    for (final PlanningCoverageFamily family : representedFamilies) {
      final CanonicalAgent familyAgent = deterministicAgentForFamily(family);
      if (familyAgent != null) {
        appendDeterministicAgent(agentsByRole, familyAgent);
      }
    }

    if (!analysis.securityRequirements().isEmpty()) {
      appendDeterministicAgent(agentsByRole, new CanonicalAgent(
          "Security Engineer",
          "Translate security requirements into phase-level controls, validation, and operational safeguards.",
          List.of("Review auth and data boundaries", "Enforce least privilege", "Validate threat-sensitive changes")));
    }

    appendDeterministicAgent(agentsByRole, new CanonicalAgent(
        "QA Engineer",
        "Keep every phase independently verifiable with explicit contract, integration, and regression coverage.",
        List.of("Define test checkpoints", "Protect regression coverage", "Validate acceptance criteria")));

    final int minimumAgentCount = profile == null || profile.shapeEnvelope() == null
        ? 3
        : Math.max(3, profile.shapeEnvelope().minAgentCount());
    final List<CanonicalAgent> fallbackAgents = List.of(
        new CanonicalAgent(
            "Platform Engineer",
            "Own deployment safety, observability, and runtime operations across the generated phases.",
            List.of("Stabilize runtime operations", "Define observability baselines", "Protect rollout safety")),
        new CanonicalAgent(
            "Data Engineer",
            "Keep data contracts, storage, and migration boundaries stable across workstreams.",
            List.of("Review schema changes", "Protect persistence contracts", "Validate data lifecycle changes")),
        new CanonicalAgent(
            "Integration Engineer",
            "Protect external integrations, provider contracts, and retry-safe boundaries.",
            List.of("Validate third-party contracts", "Harden retry behavior", "Track provider dependencies")));
    for (final CanonicalAgent fallbackAgent : fallbackAgents) {
      if (agentsByRole.size() >= minimumAgentCount) {
        break;
      }
      appendDeterministicAgent(agentsByRole, fallbackAgent);
    }

    return List.copyOf(agentsByRole.values());
  }

  private CanonicalAgent deterministicAgentForFamily(final PlanningCoverageFamily family) {
    if (family == null) {
      return null;
    }

    return switch (family) {
      case FRONTEND_EXPERIENCE -> new CanonicalAgent(
          "Frontend Engineer",
          "Implement the user-facing slices without violating the approved API and contract boundaries.",
          List.of("Ship UI phases in order", "Protect frontend contracts", "Validate user flows"));
      case BACKEND_FOUNDATION -> new CanonicalAgent(
          "Backend Engineer",
          "Implement the core API and service backbone in the same order defined by the workstream phases.",
          List.of("Ship API contracts", "Protect service boundaries", "Keep phases independently releasable"));
      case ASYNC_PROCESSING -> new CanonicalAgent(
          "Async Processing Engineer",
          "Deliver worker, queue, and event-driven execution without coupling it to unrelated synchronous flows.",
          List.of("Implement worker boundaries", "Protect retry semantics", "Validate background execution"));
      case IDENTITY_AND_ACCESS -> new CanonicalAgent(
          "Identity Engineer",
          "Own authentication, authorization, and permission boundaries across every relevant phase.",
          List.of("Implement auth boundaries", "Validate access controls", "Protect identity contracts"));
      case TENANCY_AND_ISOLATION -> new CanonicalAgent(
          "Multi-Tenant Systems Engineer",
          "Preserve tenant isolation and organization boundaries across data, API, and operational workflows.",
          List.of("Protect tenant boundaries", "Validate isolation rules", "Review tenant lifecycle changes"));
      case OPERATIONS_AND_DEPLOYMENT -> new CanonicalAgent(
          "Platform Reliability Engineer",
          "Keep deployment, observability, and runtime operations aligned with the generated rollout phases.",
          List.of("Define rollout safety", "Implement observability baselines", "Protect runtime operability"));
      case DATA_AND_STORAGE -> new CanonicalAgent(
          "Data Engineer",
          "Own schemas, persistence flows, and state transitions required by the generated phases.",
          List.of("Review data contracts", "Protect migrations", "Validate persistence behavior"));
      case INTEGRATIONS -> new CanonicalAgent(
          "Integration Engineer",
          "Own provider-facing contracts and external integration boundaries for the generated plan.",
          List.of("Protect integration contracts", "Validate retry behavior", "Track provider dependencies"));
      case ANALYTICS_AND_QUERY -> new CanonicalAgent(
          "Analytics Engineer",
          "Own reporting, aggregation, and query-serving slices while preserving data and tenant constraints.",
          List.of("Implement reporting slices", "Protect query contracts", "Validate analytical correctness"));
      case UNKNOWN -> null;
    };
  }

  private void appendDeterministicAgent(
      final LinkedHashMap<String, CanonicalAgent> agentsByRole,
      final CanonicalAgent agent) {
    if (agent == null || agent.role().isBlank()) {
      return;
    }
    agentsByRole.putIfAbsent(agent.role(), agent);
  }

  private String synthesizeDeterministicIdeRules(
      final StructuredAnalysis analysis,
      final String ideTarget,
      final StarterSelection starterSelection) {
    final String starterKey = starterSelection.docsOnlyFallback() || starterSelection.recommendedStarter() == null
        ? "docs-only"
        : starterSelection.recommendedStarter().key();
    return """
        ## Deterministic Planning Rules

        - Target IDE: %s
        - Approved starter: %s
        - Implement phases in order and do not merge workstream boundaries.
        - Preserve the runtime topology implied by frontend=%s, backend=%s, worker=%s.
        - Keep acceptance criteria, testing, security, and observability intact for every phase.
        - Treat assumptions, unknowns, and unresolved constraints as explicit handoff context.
        """.formatted(
        ideTarget == null || ideTarget.isBlank() ? "generic" : ideTarget,
        starterKey,
        analysis.frontendNeededResolved(),
        analysis.backendNeededResolved(),
        analysis.workerNeededResolved());
  }

  private String synthesizeExecutiveSummary(
      final StructuredAnalysis analysis,
      final List<CanonicalWorkstream> workstreams) {
    final String summary = blankToDefault(analysis.summary(), "Deliver the requested project using a deterministic skeleton preview.");
    return "%s The preview is decomposed into %d workstreams so implementation can proceed safely in ordered slices."
        .formatted(summary, workstreams.size());
  }

  private String synthesizeArchitectureOverview(
      final StructuredAnalysis analysis,
      final StarterSelection starterSelection,
      final List<CanonicalWorkstream> workstreams) {
    final String starterKey = starterSelection.docsOnlyFallback() || starterSelection.recommendedStarter() == null
        ? "docs-only"
        : starterSelection.recommendedStarter().key();
    return "Runtime topology: %s. Starter alignment: %s. Workstreams cover: %s."
        .formatted(
            runtimeTopologySummary(analysis),
            starterKey,
            workstreams.stream().map(CanonicalWorkstream::title).toList());
  }

  private String synthesizeSystemContext(
      final StructuredAnalysis analysis,
      final List<CanonicalWorkstream> workstreams) {
    return "Actors: %s. Delivery channels: %s. Data sources and integrations flow through %d coordinated workstreams."
        .formatted(
            joinOrFallback(analysis.actors(), "unspecified actors"),
            joinOrFallback(analysis.deliveryChannels(), "unspecified channels"),
            workstreams.size());
  }

  private List<String> synthesizeMajorArchitectureDecisions(
      final StructuredAnalysis analysis,
      final StarterSelection starterSelection) {
    final LinkedHashSet<String> decisions = new LinkedHashSet<>();
    final String starterKey = starterSelection.docsOnlyFallback() || starterSelection.recommendedStarter() == null
        ? "docs-only"
        : starterSelection.recommendedStarter().key();
    decisions.add("Use the approved starter %s as the implementation baseline.".formatted(starterKey));
    decisions.add("Preserve the runtime topology of %s across all phases.".formatted(runtimeTopologySummary(analysis)));
    if (analysis.tenancyModel() != null && !analysis.tenancyModel().isBlank()) {
      decisions.add("Model tenancy using the declared strategy: %s.".formatted(analysis.tenancyModel()));
    }
    if (analysis.deploymentModel() != null && !analysis.deploymentModel().isBlank()) {
      decisions.add("Keep deployment assumptions aligned with %s constraints.".formatted(analysis.deploymentModel()));
    }
    if (!analysis.integrationPoints().isEmpty()) {
      decisions.add("Treat external integrations as explicit contracts instead of implicit side effects.");
    }
    return List.copyOf(decisions);
  }

  private List<String> synthesizeNonFunctionalRequirements(final StructuredAnalysis analysis) {
    final LinkedHashSet<String> requirements = new LinkedHashSet<>();
    collectFallbackItems(requirements, analysis.securityRequirements(), 2);
    collectFallbackItems(requirements, analysis.complianceRequirements(), 1);
    collectFallbackItems(requirements, analysis.operationalConstraints(), 2);
    if (analysis.scaleExpectations() != null && !analysis.scaleExpectations().isBlank()) {
      requirements.add("Scale expectation: %s".formatted(analysis.scaleExpectations()));
    }
    if (analysis.latencyExpectations() != null && !analysis.latencyExpectations().isBlank()) {
      requirements.add("Latency expectation: %s".formatted(analysis.latencyExpectations()));
    }
    if (requirements.isEmpty()) {
      requirements.add("Preserve contract stability, observability, and safe incremental rollout.");
    }
    return List.copyOf(requirements);
  }

  private List<String> synthesizeCrossCuttingConcerns(final StructuredAnalysis analysis) {
    final LinkedHashSet<String> concerns = new LinkedHashSet<>();
    collectFallbackItems(concerns, analysis.securityRequirements(), 2);
    collectFallbackItems(concerns, analysis.integrationPoints(), 2);
    collectFallbackItems(concerns, analysis.dataSources(), 2);
    if (concerns.isEmpty()) {
      concerns.add("Preserve isolation between workstreams and phase handoffs.");
    }
    return List.copyOf(concerns);
  }

  private List<String> synthesizeGlossary(
      final StructuredAnalysis analysis,
      final List<CanonicalWorkstream> workstreams) {
    final LinkedHashSet<String> glossary = new LinkedHashSet<>();
    collectFallbackItems(glossary, analysis.techStack(), 3);
    collectFallbackItems(glossary, analysis.integrationPoints(), 2);
    collectFallbackItems(glossary, workstreams.stream().map(CanonicalWorkstream::title).toList(), 3);
    return List.copyOf(glossary);
  }

  private String buildDeterministicCoverageCorpus(final List<CanonicalWorkstream> workstreams) {
    if (workstreams == null || workstreams.isEmpty()) {
      return "";
    }

    final List<String> fragments = new ArrayList<>();
    fragments.addAll(workstreams.stream().map(CanonicalWorkstream::title).toList());
    fragments.addAll(workstreams.stream().map(CanonicalWorkstream::goal).toList());
    fragments.addAll(workstreams.stream().map(CanonicalWorkstream::whyItExists).toList());
    fragments.addAll(workstreams.stream().flatMap(workstream -> workstream.includedScope().stream()).toList());
    return String.join(" ", fragments).toLowerCase(Locale.ROOT);
  }

  private List<String> selectRelevantItems(
      final List<String> candidates,
      final String corpus,
      final int maxItems) {
    final LinkedHashSet<String> selectedItems = new LinkedHashSet<>();
    collectRelevantItems(selectedItems, candidates, corpus, maxItems);
    if (selectedItems.isEmpty()) {
      collectFallbackItems(selectedItems, candidates, maxItems);
    }
    return List.copyOf(selectedItems);
  }

  private String resolvedProjectName(final StructuredAnalysis analysis) {
    return blankToDefault(analysis.projectName(), "Deterministic Preview Project");
  }

  private String runtimeTopologySummary(final StructuredAnalysis analysis) {
    final List<String> topology = new ArrayList<>();
    if (analysis.frontendNeededResolved()) {
      topology.add("frontend");
    }
    if (analysis.backendNeededResolved()) {
      topology.add("backend");
    }
    if (analysis.workerNeededResolved()) {
      topology.add("worker");
    }
    return topology.isEmpty() ? "single-surface runtime" : String.join(" + ", topology);
  }

  private String joinOrFallback(final List<String> values, final String fallback) {
    return values == null || values.isEmpty() ? fallback : String.join(", ", values);
  }

  private String blankToDefault(final String value, final String fallback) {
    return value == null || value.isBlank() ? fallback : value.trim();
  }

  private String firstNonBlank(final List<String> values) {
    if (values == null || values.isEmpty()) {
      return null;
    }
    for (final String value : values) {
      if (value != null && !value.isBlank()) {
        return value.trim();
      }
    }
    return null;
  }

  private String firstNonBlank(
      final List<String> values,
      final String fallback,
      final String finalFallback) {
    final String value = firstNonBlank(values);
    if (value != null) {
      return value;
    }
    if (fallback != null && !fallback.isBlank()) {
      return fallback.trim();
    }
    return finalFallback;
  }

  private String firstNonBlank(final String primary, final String secondary, final String fallback) {
    if (primary != null && !primary.isBlank()) {
      return primary.trim();
    }
    if (secondary != null && !secondary.isBlank()) {
      return secondary.trim();
    }
    return fallback;
  }

  private PhaseExpansionPass callPhaseExpansionPass(
      final DeterministicPlanningProfile profile,
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final StructuredAnalysis analysis,
      final StarterSelection starterSelection,
      final WorkstreamDecompositionPass workstreamDecomposition,
      final boolean reducedMode) {
    final List<CanonicalWorkstream> expectedWorkstreams = workstreamDecomposition.workstreams();
    final int requiredPhaseFloor = requiredPhaseFloor(profile, expectedWorkstreams.size(), plannerModelRef, reducedMode);

    if (shouldUseBatchedPhaseExpansion(profile, analysis, plannerModelRef, expectedWorkstreams)) {
      return callBatchedPhaseExpansionPass(
          profile,
          planningComplexity,
          plannerModelRef,
          executionRoute,
          generationUsageContext,
          heavyChatModel,
          analysis,
          starterSelection,
          reducedMode,
          expectedWorkstreams,
          requiredPhaseFloor);
    }

    final String expansionStrategyNotes = buildPhaseExpansionStrategyNotes(
        "single_phase_expansion",
        null,
        null,
        requiredPhaseFloor);
    return callPhaseExpansionPromptPass(
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        SystemPrompts.PHASE_EXPANDER,
        PromptTemplates.phaseExpansionPrompt(
            profile,
            analysis,
            starterSelection,
            workstreamDecomposition,
            reducedMode,
            expansionStrategyNotes),
        SystemPrompts.COMPACT_PHASE_EXPANDER,
        PromptTemplates.compactPhaseExpansionPrompt(
            profile,
            analysis,
            starterSelection,
            workstreamDecomposition,
            reducedMode,
            expansionStrategyNotes),
        "phase expansion",
        phaseExpansion -> validatePhaseExpansionPass(
            "phase expansion",
            expectedWorkstreams,
            phaseExpansion,
            requiredPhaseFloor));
  }

  private PhaseExpansionPass callBatchedPhaseExpansionPass(
      final DeterministicPlanningProfile profile,
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final StructuredAnalysis analysis,
      final StarterSelection starterSelection,
      final boolean reducedMode,
      final List<CanonicalWorkstream> expectedWorkstreams,
      final int requiredPhaseFloor) {
    final List<PhaseExpansionBatch> batches = buildPhaseExpansionBatches(expectedWorkstreams, requiredPhaseFloor);
    final List<CanonicalWorkstream> expandedWorkstreams = new ArrayList<>(expectedWorkstreams.size());
    log.info("Using batched phase expansion - workstreams={}, batches={}", expectedWorkstreams.size(), batches.size());

    for (final PhaseExpansionBatch batch : batches) {
      final String expansionStrategyNotes = buildPhaseExpansionStrategyNotes(
          "batched_phase_expansion",
          batch.batchNumber(),
          batch.batchCount(),
          batch.requiredPhaseFloor());
      final WorkstreamDecompositionPass batchDecomposition = new WorkstreamDecompositionPass(batch.workstreams());
      final String passName = "phase expansion batch " + batch.batchNumber();
      final PhaseExpansionPass validatedBatch = callPhaseExpansionPromptPass(
          planningComplexity,
          plannerModelRef,
          executionRoute,
          generationUsageContext,
          heavyChatModel,
          SystemPrompts.PHASE_EXPANDER,
          PromptTemplates.phaseExpansionPrompt(
              profile,
              analysis,
              starterSelection,
              batchDecomposition,
              reducedMode,
              expansionStrategyNotes),
          SystemPrompts.COMPACT_PHASE_EXPANDER,
          PromptTemplates.compactPhaseExpansionPrompt(
              profile,
              analysis,
              starterSelection,
              batchDecomposition,
              reducedMode,
              expansionStrategyNotes),
          passName,
          phaseExpansion -> validatePhaseExpansionPass(
              passName,
              batch.workstreams(),
              phaseExpansion,
              batch.requiredPhaseFloor()));
      expandedWorkstreams.addAll(alignWorkstreamOrder(batch.workstreams(), validatedBatch.workstreams()));
    }

    return validatePhaseExpansionPass(
        "phase expansion",
        expectedWorkstreams,
        new PhaseExpansionPass(expandedWorkstreams),
        requiredPhaseFloor);
  }


  private PhaseExpansionPass callTargetedPhaseExpansionPass(
      final DeterministicPlanningProfile profile,
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final StructuredAnalysis analysis,
      final StarterSelection starterSelection,
      final CanonicalBlueprint currentPlan) {
    final List<CanonicalWorkstream> expectedWorkstreams = currentPlan.workstreams();
    final int requiredPhaseFloor = requiredPhaseFloor(profile, expectedWorkstreams.size(), plannerModelRef, false);
    final String repairStrategyNotes = buildTargetedRepairStrategyNotes(
        "targeted_phase_repair",
        requiredPhaseFloor);
    PhaseExpansionPass phaseExpansion = callPhaseExpansionPromptPass(
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        SystemPrompts.TARGETED_PHASE_EXPANDER,
        PromptTemplates.targetedPhaseExpansionPrompt(profile, analysis, starterSelection, currentPlan, repairStrategyNotes),
        SystemPrompts.COMPACT_TARGETED_PHASE_EXPANDER,
        PromptTemplates.compactTargetedPhaseExpansionPrompt(
            profile,
            analysis,
            starterSelection,
            currentPlan,
            repairStrategyNotes),
        "targeted phase expansion",
        this::validatePhaseExpansionViability);

    final PhaseCoverageValidation initialCoverage = inspectPhaseExpansionCoverage(
        expectedWorkstreams,
        normalizePhaseExpansionPass(phaseExpansion, expectedWorkstreams).workstreams());
    if (initialCoverage.hasWorkstreamCoverageGap()) {
      phaseExpansion = restoreMissingTargetedWorkstreams(
          profile,
          planningComplexity,
          plannerModelRef,
          executionRoute,
          generationUsageContext,
          heavyChatModel,
          analysis,
          starterSelection,
          currentPlan,
          phaseExpansion,
          initialCoverage,
          requiredPhaseFloor);
    }
    return validatePhaseExpansionPass("targeted phase expansion", expectedWorkstreams, phaseExpansion, requiredPhaseFloor);
  }

  private PhaseExpansionPass restoreMissingTargetedWorkstreams(
      final DeterministicPlanningProfile profile,
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final StructuredAnalysis analysis,
      final StarterSelection starterSelection,
      final CanonicalBlueprint currentPlan,
      final PhaseExpansionPass repairedPhaseExpansion,
      final PhaseCoverageValidation initialCoverage,
      final int requiredPhaseFloor) {
    final List<CanonicalWorkstream> missingWorkstreams = selectExpectedWorkstreams(
        currentPlan.workstreams(),
        initialCoverage.workstreamsMissingCoverage());
    if (missingWorkstreams.isEmpty()) {
      return repairedPhaseExpansion;
    }

    final int restorationPhaseFloor = Math.max(
        missingWorkstreams.size(),
        Math.max(0, requiredPhaseFloor - initialCoverage.totalPhases()));
    final String repairStrategyNotes = buildTargetedRepairStrategyNotes(
        "targeted_workstream_restoration",
        restorationPhaseFloor);
    final PhaseExpansionPass validatedRestoredWorkstreams = callPhaseExpansionPromptPass(
        planningComplexity,
        plannerModelRef,
        executionRoute,
        generationUsageContext,
        heavyChatModel,
        SystemPrompts.TARGETED_PHASE_EXPANDER,
        PromptTemplates.targetedWorkstreamRestorationPrompt(
            profile,
            analysis,
            starterSelection,
            currentPlan,
            missingWorkstreams,
            repairStrategyNotes),
        SystemPrompts.COMPACT_TARGETED_PHASE_EXPANDER,
        PromptTemplates.compactTargetedWorkstreamRestorationPrompt(
            profile,
            analysis,
            starterSelection,
            currentPlan,
            missingWorkstreams,
            repairStrategyNotes),
        "targeted phase coverage restoration",
        phaseExpansion -> validatePhaseExpansionPass(
            "targeted phase coverage restoration",
            missingWorkstreams,
            phaseExpansion,
            restorationPhaseFloor));
    return new PhaseExpansionPass(mergeWorkstreamCoverage(
        currentPlan.workstreams(),
        repairedPhaseExpansion.workstreams(),
        validatedRestoredWorkstreams.workstreams()));
  }

  private PhaseExpansionPass callPhaseExpansionPromptPass(
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final String standardSystemPrompt,
      final String standardUserPrompt,
      final String compactSystemPrompt,
      final String compactUserPrompt,
      final String passName,
      final Function<PhaseExpansionPass, PhaseExpansionPass> semanticValidator) {
    try {
      return semanticValidator.apply(normalizePhaseExpansionPass(callPass(
          planningComplexity,
          plannerModelRef,
          executionRoute,
          generationUsageContext,
          heavyChatModel,
          standardSystemPrompt,
          standardUserPrompt,
          PhaseExpansionPass.class,
          passName)));
    } catch (final LlmResponseParseException exception) {
      return recoverCompactPhaseExpansion(
          planningComplexity,
          plannerModelRef,
          executionRoute,
          generationUsageContext,
          heavyChatModel,
          compactSystemPrompt,
          compactUserPrompt,
          passName,
          semanticValidator,
          exception);
    }
  }

  private PhaseExpansionPass recoverCompactPhaseExpansion(
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final String systemPrompt,
      final String userPrompt,
      final String passName,
      final Function<PhaseExpansionPass, PhaseExpansionPass> semanticValidator,
      final LlmResponseParseException originalException) {
    log.warn(
        "Falling back to compact {} rescue after parse/validation failure: reason={}",
        passName,
        originalException.getMessage());

    try {
      final PhaseExpansionPass compactRescueResult = semanticValidator.apply(normalizePhaseExpansionPass(callPass(
          planningComplexity,
          plannerModelRef,
          executionRoute,
          generationUsageContext,
          heavyChatModel,
          systemPrompt,
          userPrompt,
          PhaseExpansionPass.class,
          passName + " compact rescue")));
      planningTelemetryService.recordCompactRescueUsage(
          passName,
          planningComplexity,
          plannerModelRef,
          "compact_phase_expansion",
          true);
      return compactRescueResult;
    } catch (final LlmResponseValidationException rescueException) {
      planningTelemetryService.recordCompactRescueUsage(
          passName,
          planningComplexity,
          plannerModelRef,
          "compact_phase_expansion",
          false);
      rescueException.addSuppressed(originalException);
      throw rescueException;
    } catch (final LlmResponseParseException rescueException) {
      planningTelemetryService.recordCompactRescueUsage(
          passName,
          planningComplexity,
          plannerModelRef,
          "compact_phase_expansion",
          false);
      rescueException.addSuppressed(originalException);
      throw new LlmResponseParseException("Failed to parse multi-pass blueprint output for " + passName,
          rescueException);
    }
  }

  private <T> T callPass(
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ChatModel heavyChatModel,
      final String systemPrompt,
      final String userPrompt,
      final Class<T> responseType,
      final String passName) {
    return callPass(
        planningComplexity,
        plannerModelRef,
        null,
        GenerationUsageContext.empty(),
        heavyChatModel,
        systemPrompt,
        userPrompt,
        responseType,
        passName);
  }

  private <T> T callPass(
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final String systemPrompt,
      final String userPrompt,
      final Class<T> responseType,
      final String passName) {
    LlmResponseParseException lastParseException = null;

    for (int attempt = 1; attempt <= MAX_PASS_PARSE_ATTEMPTS; attempt++) {
      final String effectiveUserPrompt = attempt == 1 ? userPrompt : buildRetryUserPrompt(userPrompt);
      final PassInvocation invocation = invokePass(
          planningComplexity,
          plannerModelRef,
          executionRoute,
          generationUsageContext,
          heavyChatModel,
          systemPrompt,
          effectiveUserPrompt,
          passName,
          attempt);

      try {
        final T parsedResponse = parseResponse(invocation.rawJson(), responseType, passName);
        capturePassUsage(
            generationUsageContext,
            executionRoute,
            invocation,
            AiUsageEventStatus.COMPLETED,
            passName,
            attempt,
            null);
        return parsedResponse;
      } catch (final LlmResponseParseException exception) {
        capturePassUsage(
            generationUsageContext,
            executionRoute,
            invocation,
            AiUsageEventStatus.FAILED,
            passName,
            attempt,
            Map.of(
                "failureReason", exception.getClass().getSimpleName(),
                "failureMessage", exception.getMessage() == null ? "unknown" : exception.getMessage()));
        lastParseException = exception;
        if (attempt < MAX_PASS_PARSE_ATTEMPTS) {
          log.warn("Retrying {} after parse failure: attempt={}, reason={}", passName, attempt, exception.getMessage());
        }
      }
    }

    throw lastParseException == null
        ? new LlmResponseParseException("Failed to parse multi-pass blueprint output for " + passName)
        : new LlmResponseParseException("Failed to parse multi-pass blueprint output for " + passName, lastParseException);
  }

  private PassInvocation invokePass(
      final PlanningComplexity planningComplexity,
      final PlannerModelRef plannerModelRef,
      final ResolvedAiExecutionRoute executionRoute,
      final GenerationUsageContext generationUsageContext,
      final ChatModel heavyChatModel,
      final String systemPrompt,
      final String userPrompt,
      final String passName,
      final int attempt) {
    final Prompt prompt = new Prompt(List.of(
        new SystemMessage(systemPrompt),
        new UserMessage(userPrompt)));
    final OffsetDateTime startedAt = OffsetDateTime.now();
    final long startedAtNanos = System.nanoTime();
    try {
      final ChatResponse response = heavyChatModel.call(prompt);
      final String rawJson = response.getResult().getOutput().getText();
      planningTelemetryService.recordPassExecution(
          passName,
          planningComplexity,
          plannerModelRef,
          providerReportedTotalTokens(response),
          safeLength(systemPrompt) + safeLength(userPrompt),
          safeLength(rawJson),
          (System.nanoTime() - startedAtNanos) / 1_000_000L);
      if (attempt > 1) {
        log.info("{} retry completed: attempt={}, responseLength={}", passName, attempt, safeLength(rawJson));
      }
      return new PassInvocation(prompt, response, rawJson, startedAt, OffsetDateTime.now());
    } catch (final RuntimeException exception) {
      capturePassFailure(
          generationUsageContext,
          executionRoute,
          prompt,
          null,
          null,
          startedAt,
          passName,
          attempt,
          exception);
      throw exception;
    }
  }

  private <T> T parseResponse(final String rawJson, final Class<T> responseType, final String passName) {
    try {
      return objectMapper.readValue(LlmResponseSanitizer.extractJson(rawJson), responseType);
    } catch (final LlmResponseParseException exception) {
      throw exception;
    } catch (final Exception exception) {
      throw new LlmResponseParseException("Failed to parse multi-pass blueprint output for " + passName, exception);
    }
  }

  private String buildRetryUserPrompt(final String userPrompt) {
    return userPrompt + JSON_RETRY_SUFFIX;
  }

  private PhaseExpansionPass normalizePhaseExpansionPass(final PhaseExpansionPass phaseExpansion) {
    return normalizePhaseExpansionPass(phaseExpansion, List.of());
  }

  private PhaseExpansionPass normalizePhaseExpansionPass(
      final PhaseExpansionPass phaseExpansion,
      final List<CanonicalWorkstream> expectedWorkstreams) {
    if (phaseExpansion == null || phaseExpansion.workstreams().isEmpty()) {
      return phaseExpansion == null ? new PhaseExpansionPass(List.of()) : phaseExpansion;
    }

    final List<CanonicalWorkstream> orderedWorkstreams = alignWorkstreamOrder(expectedWorkstreams, phaseExpansion.workstreams());
    final List<CanonicalWorkstream> resequencedWorkstreams = resequenceWorkstreamPhases(orderedWorkstreams);
    return new PhaseExpansionPass(resequencedWorkstreams);
  }

  private boolean shouldUseBatchedPhaseExpansion(
      final DeterministicPlanningProfile profile,
      final StructuredAnalysis analysis,
      final PlannerModelRef plannerModelRef,
      final List<CanonicalWorkstream> workstreams) {
    final int workstreamCount = workstreams == null ? 0 : workstreams.size();
    if (workstreamCount < 2) {
      return false;
    }

    final boolean enterpriseComplexity = profile != null
        && profile.effectiveComplexity() == PlanningComplexity.ENTERPRISE
        && workstreamCount >= 3;
    final boolean highWorkstreamCount = workstreamCount >= BATCH_PHASE_EXPANSION_WORKSTREAM_THRESHOLD;
    final boolean riskyHardSignalMix = profile != null
        && profile.hardSignals().contains(PlanningHardSignal.MULTI_TENANCY)
        && profile.hardSignals().contains(PlanningHardSignal.SELF_HOSTED_DEPLOYMENT)
        && profile.hardSignals().contains(PlanningHardSignal.ASYNC_PROCESSING)
        && profile.hardSignals().contains(PlanningHardSignal.DASHBOARD_AND_API)
        && workstreamCount >= 3;
    final boolean lowCapabilityTokenRisk = plannerModelRef != null
        && (plannerModelRef.modelCapability() == ModelCapability.LOW
            || plannerModelRef.modelCapability() == ModelCapability.MODERATE)
        && workstreamCount >= 3
        && hasHighPhaseTokenRisk(profile, analysis, workstreamCount);
    return enterpriseComplexity || highWorkstreamCount || riskyHardSignalMix || lowCapabilityTokenRisk;
  }

  private boolean hasHighPhaseTokenRisk(
      final DeterministicPlanningProfile profile,
      final StructuredAnalysis analysis,
      final int workstreamCount) {
    final int phaseFloor = requiredPhaseFloor(profile, workstreamCount, null, false);
    final boolean broadRuntimeTopology = analysis != null
        && analysis.frontendNeededResolved()
        && analysis.backendNeededResolved()
        && analysis.workerNeededResolved();
    final boolean multipleIntegrationSurfaces = analysis != null
        && (analysis.integrationPoints().size() > 1
            || analysis.dataSources().size() > 1);
    return phaseFloor >= 8 || (phaseFloor >= 6 && (broadRuntimeTopology || multipleIntegrationSurfaces));
  }

  private int requiredPhaseFloor(
      final DeterministicPlanningProfile profile,
      final int expectedWorkstreamCount,
      final PlannerModelRef plannerModelRef,
      final boolean reducedMode) {
    final PlanningShapeEnvelope shapeEnvelope = profile == null ? null : profile.shapeEnvelope();
    final int envelopeFloor = shapeEnvelope == null ? 0 : shapeEnvelope.minPhaseCount();
    final int baseFloor = Math.max(Math.max(0, expectedWorkstreamCount), envelopeFloor);

    if (!shouldRelaxPhaseFloor(profile, plannerModelRef, reducedMode, baseFloor)) {
      return baseFloor;
    }

    return Math.max(expectedWorkstreamCount, Math.min(baseFloor, expectedWorkstreamCount + 3));
  }

  private boolean shouldRelaxPhaseFloor(
      final DeterministicPlanningProfile profile,
      final PlannerModelRef plannerModelRef,
      final boolean reducedMode,
      final int baseFloor) {
    if (baseFloor <= 0) {
      return false;
    }

    if (reducedMode) {
      return true;
    }

    if (profile == null || profile.effectiveComplexity() != PlanningComplexity.ENTERPRISE || plannerModelRef == null) {
      return false;
    }

    return plannerModelRef.modelCapability() != ModelCapability.HIGH;
  }

  private List<PhaseExpansionBatch> buildPhaseExpansionBatches(
      final List<CanonicalWorkstream> expectedWorkstreams,
      final int requiredPhaseFloor) {
    if (expectedWorkstreams == null || expectedWorkstreams.isEmpty()) {
      return List.of();
    }

    final int batchCount = (expectedWorkstreams.size() + DEFAULT_PHASE_EXPANSION_BATCH_SIZE - 1)
        / DEFAULT_PHASE_EXPANSION_BATCH_SIZE;
    final int baseFloor = batchCount == 0 ? 0 : requiredPhaseFloor / batchCount;
    final int remainder = batchCount == 0 ? 0 : requiredPhaseFloor % batchCount;
    final List<PhaseExpansionBatch> batches = new ArrayList<>(batchCount);

    for (int batchIndex = 0; batchIndex < batchCount; batchIndex++) {
      final int fromIndex = batchIndex * DEFAULT_PHASE_EXPANSION_BATCH_SIZE;
      final int toIndex = Math.min(fromIndex + DEFAULT_PHASE_EXPANSION_BATCH_SIZE, expectedWorkstreams.size());
      final List<CanonicalWorkstream> batchWorkstreams = List.copyOf(expectedWorkstreams.subList(fromIndex, toIndex));
      final int allocatedFloor = Math.max(
          batchWorkstreams.size(),
          baseFloor + (batchIndex < remainder ? 1 : 0));
      batches.add(new PhaseExpansionBatch(batchIndex + 1, batchCount, allocatedFloor, batchWorkstreams));
    }
    return List.copyOf(batches);
  }

  private PhaseExpansionPass validatePhaseExpansionPass(
      final String passName,
      final List<CanonicalWorkstream> expectedWorkstreams,
      final PhaseExpansionPass phaseExpansion,
      final int requiredPhaseFloor) {
    final PhaseExpansionPass normalizedPhaseExpansion = normalizePhaseExpansionPass(phaseExpansion, expectedWorkstreams);
    planningPassValidationService.validatePhaseExpansion(
        passName,
        expectedWorkstreams,
        normalizedPhaseExpansion.workstreams(),
        requiredPhaseFloor);
    return normalizedPhaseExpansion;
  }

  private PhaseExpansionPass validatePhaseExpansionViability(
      final PhaseExpansionPass phaseExpansion) {
    final PhaseExpansionPass normalizedPhaseExpansion = normalizePhaseExpansionPass(phaseExpansion);
    planningPassValidationService.validatePhaseExpansionViability(
        "targeted phase expansion",
        normalizedPhaseExpansion.workstreams());
    return normalizedPhaseExpansion;
  }

  private WorkstreamDecompositionPass validateWorkstreamDecompositionPass(
      final String passName,
      final WorkstreamDecompositionPass workstreamDecomposition) {
    planningPassValidationService.validateWorkstreamDecomposition(
        passName,
        workstreamDecomposition.workstreams(),
        1);
    return workstreamDecomposition;
  }

  private PhaseCoverageValidation inspectPhaseExpansionCoverage(
      final List<CanonicalWorkstream> expectedWorkstreams,
      final List<CanonicalWorkstream> expandedWorkstreams) {
    if (expectedWorkstreams == null || expectedWorkstreams.isEmpty()) {
      return new PhaseCoverageValidation(List.of(), List.of(), flattenWorkstreamPhases(expandedWorkstreams).size());
    }

    final Map<String, CanonicalWorkstream> expandedWorkstreamsByTitle = indexWorkstreamsByTitle(expandedWorkstreams);
    final List<String> missingWorkstreams = new ArrayList<>();
    final List<String> workstreamsWithoutPhases = new ArrayList<>();

    for (final CanonicalWorkstream expectedWorkstream : expectedWorkstreams) {
      if (expectedWorkstream == null || expectedWorkstream.title().isBlank()) {
        continue;
      }

      final CanonicalWorkstream expandedWorkstream = expandedWorkstreamsByTitle.get(
          normalizeWorkstreamKey(expectedWorkstream.title()));
      if (expandedWorkstream == null) {
        missingWorkstreams.add(expectedWorkstream.title());
        continue;
      }
      if (expandedWorkstream.phases().isEmpty()) {
        workstreamsWithoutPhases.add(expectedWorkstream.title());
      }
    }

    return new PhaseCoverageValidation(
        List.copyOf(missingWorkstreams),
        List.copyOf(workstreamsWithoutPhases),
        flattenWorkstreamPhases(expandedWorkstreams).size());
  }

  private String buildPhaseExpansionStrategyNotes(
      final String expansionMode,
      final Integer batchNumber,
      final Integer batchCount,
      final int requiredPhaseFloor) {
    final String batchContext = batchNumber == null || batchCount == null
        ? "- Batch context: none"
        : "- Current batch: %d/%d".formatted(batchNumber, batchCount);
    return """
        - Expansion mode: %s
        - Required phase floor for this pass: %d
        - Every listed workstream must receive at least one phase.
        - Return only the listed workstreams with nested phases.
        %s
        """.formatted(
        expansionMode,
        requiredPhaseFloor,
        batchContext);
  }

  private String buildTargetedRepairStrategyNotes(
      final String repairMode,
      final int requiredPhaseFloor) {
    return """
        - Repair mode: %s
        - Required phase floor for this pass: %d
        - Every listed target workstream must receive at least one phase.
        - Return only nested workstreams and preserve the listed workstream titles.
        """.formatted(repairMode, requiredPhaseFloor);
  }

  private List<CanonicalWorkstream> alignWorkstreamOrder(
      final List<CanonicalWorkstream> expectedWorkstreams,
      final List<CanonicalWorkstream> expandedWorkstreams) {
    if (expandedWorkstreams == null || expandedWorkstreams.isEmpty()) {
      return List.of();
    }
    if (expectedWorkstreams == null || expectedWorkstreams.isEmpty()) {
      return List.copyOf(expandedWorkstreams);
    }

    final Map<String, CanonicalWorkstream> expandedWorkstreamsByTitle = indexWorkstreamsByTitle(expandedWorkstreams);
    final List<CanonicalWorkstream> orderedWorkstreams = new ArrayList<>(expandedWorkstreams.size());
    final Map<String, Boolean> appendedKeys = new LinkedHashMap<>();

    for (final CanonicalWorkstream expectedWorkstream : expectedWorkstreams) {
      if (expectedWorkstream == null || expectedWorkstream.title().isBlank()) {
        continue;
      }

      final String key = normalizeWorkstreamKey(expectedWorkstream.title());
      final CanonicalWorkstream expandedWorkstream = expandedWorkstreamsByTitle.get(key);
      if (expandedWorkstream != null && !appendedKeys.containsKey(key)) {
        orderedWorkstreams.add(expandedWorkstream);
        appendedKeys.put(key, Boolean.TRUE);
      }
    }

    for (final CanonicalWorkstream expandedWorkstream : expandedWorkstreams) {
      if (expandedWorkstream == null || expandedWorkstream.title().isBlank()) {
        continue;
      }

      final String key = normalizeWorkstreamKey(expandedWorkstream.title());
      if (!appendedKeys.containsKey(key)) {
        orderedWorkstreams.add(expandedWorkstream);
        appendedKeys.put(key, Boolean.TRUE);
      }
    }
    return List.copyOf(orderedWorkstreams);
  }

  private List<CanonicalWorkstream> mergeWorkstreamCoverage(
      final List<CanonicalWorkstream> expectedWorkstreams,
      final List<CanonicalWorkstream> repairedWorkstreams,
      final List<CanonicalWorkstream> restoredWorkstreams) {
    final Map<String, CanonicalWorkstream> repairedByTitle = indexWorkstreamsByTitle(repairedWorkstreams);
    final Map<String, CanonicalWorkstream> restoredByTitle = indexWorkstreamsByTitle(restoredWorkstreams);
    final List<CanonicalWorkstream> mergedWorkstreams = new ArrayList<>();
    final Map<String, Boolean> appendedKeys = new LinkedHashMap<>();

    for (final CanonicalWorkstream expectedWorkstream : expectedWorkstreams) {
      if (expectedWorkstream == null || expectedWorkstream.title().isBlank()) {
        continue;
      }

      final String key = normalizeWorkstreamKey(expectedWorkstream.title());
      CanonicalWorkstream mergedWorkstream = repairedByTitle.get(key);
      if (mergedWorkstream == null || mergedWorkstream.phases().isEmpty()) {
        mergedWorkstream = restoredByTitle.get(key);
      }
      if (mergedWorkstream != null) {
        mergedWorkstreams.add(mergedWorkstream);
        appendedKeys.put(key, Boolean.TRUE);
      }
    }

    appendExtraWorkstreams(mergedWorkstreams, repairedWorkstreams, appendedKeys);
    appendExtraWorkstreams(mergedWorkstreams, restoredWorkstreams, appendedKeys);
    return List.copyOf(mergedWorkstreams);
  }

  private void appendExtraWorkstreams(
      final List<CanonicalWorkstream> mergedWorkstreams,
      final List<CanonicalWorkstream> sourceWorkstreams,
      final Map<String, Boolean> appendedKeys) {
    if (sourceWorkstreams == null || sourceWorkstreams.isEmpty()) {
      return;
    }

    for (final CanonicalWorkstream workstream : sourceWorkstreams) {
      if (workstream == null || workstream.title().isBlank()) {
        continue;
      }

      final String key = normalizeWorkstreamKey(workstream.title());
      if (!appendedKeys.containsKey(key)) {
        mergedWorkstreams.add(workstream);
        appendedKeys.put(key, Boolean.TRUE);
      }
    }
  }

  private List<CanonicalWorkstream> selectExpectedWorkstreams(
      final List<CanonicalWorkstream> expectedWorkstreams,
      final List<String> targetTitles) {
    if (expectedWorkstreams == null || expectedWorkstreams.isEmpty() || targetTitles == null || targetTitles.isEmpty()) {
      return List.of();
    }

    final Map<String, CanonicalWorkstream> expectedWorkstreamsByTitle = indexWorkstreamsByTitle(expectedWorkstreams);
    final List<CanonicalWorkstream> selectedWorkstreams = new ArrayList<>(targetTitles.size());
    for (final String targetTitle : targetTitles) {
      final CanonicalWorkstream workstream = expectedWorkstreamsByTitle.get(normalizeWorkstreamKey(targetTitle));
      if (workstream != null) {
        selectedWorkstreams.add(workstream);
      }
    }
    return List.copyOf(selectedWorkstreams);
  }

  private Map<String, CanonicalWorkstream> indexWorkstreamsByTitle(final List<CanonicalWorkstream> workstreams) {
    final Map<String, CanonicalWorkstream> indexedWorkstreams = new LinkedHashMap<>();
    if (workstreams == null || workstreams.isEmpty()) {
      return indexedWorkstreams;
    }

    for (final CanonicalWorkstream workstream : workstreams) {
      if (workstream == null || workstream.title().isBlank()) {
        continue;
      }
      indexedWorkstreams.putIfAbsent(normalizeWorkstreamKey(workstream.title()), workstream);
    }
    return indexedWorkstreams;
  }

  private String normalizeWorkstreamKey(final String title) {
    if (title == null) {
      return "";
    }
    return title.trim().toLowerCase(Locale.ROOT);
  }

  private List<CanonicalWorkstream> resequenceWorkstreamPhases(final List<CanonicalWorkstream> workstreams) {
    final List<CanonicalWorkstream> resequencedWorkstreams = new ArrayList<>(workstreams.size());
    int nextPhaseOrder = 1;

    for (final CanonicalWorkstream workstream : workstreams) {
      final List<CanonicalPhase> resequencedPhases = new ArrayList<>(workstream.phases().size());
      for (final CanonicalPhase phase : workstream.phases()) {
        resequencedPhases.add(withOrder(phase, nextPhaseOrder++));
      }

      resequencedWorkstreams.add(new CanonicalWorkstream(
          workstream.title(),
          workstream.goal(),
          workstream.whyItExists(),
          workstream.includedScope(),
          workstream.excludedScope(),
          workstream.dependencies(),
          workstream.suggestedAgents(),
          resequencedPhases));
    }

    return List.copyOf(resequencedWorkstreams);
  }

  private List<CanonicalPhase> flattenWorkstreamPhases(final List<CanonicalWorkstream> workstreams) {
    if (workstreams == null || workstreams.isEmpty()) {
      return List.of();
    }

    return workstreams.stream().flatMap(workstream -> workstream.phases().stream()).toList();
  }

  private CanonicalPhase withOrder(final CanonicalPhase phase, final int order) {
    return new CanonicalPhase(
        order,
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
        phase.handoffInstructions());
  }

  private String mergeStarterNotes(final String aiStarterNotes, final StarterSelection starterSelection) {
    final String deterministicNotes = buildDeterministicStarterNotes(starterSelection);
    final String normalizedAiStarterNotes = aiStarterNotes == null ? "" : aiStarterNotes.trim();
    if (normalizedAiStarterNotes.isBlank()) {
      return deterministicNotes;
    }
    return deterministicNotes + "\n\n## Multi-Pass Planning Notes\n\n" + normalizedAiStarterNotes;
  }

  private String buildDeterministicStarterNotes(final StarterSelection starterSelection) {
    final StringBuilder notesBuilder = new StringBuilder();
    notesBuilder.append("## Starter Resolution\n\n");
    if (starterSelection.docsOnlyFallback()) {
      notesBuilder.append("Mode: docs-only\n\n");
      notesBuilder.append("Reason: ").append(starterSelection.docsOnlyFallbackReason());
      return notesBuilder.toString();
    }

    final StarterCandidate recommendedStarter = starterSelection.recommendedStarter();
    notesBuilder.append("Recommended starter: ")
        .append(recommendedStarter.key())
        .append(" (")
        .append(recommendedStarter.title())
        .append(")\n");
    notesBuilder.append("Source: ")
        .append(recommendedStarter.sourceKind().value())
        .append(" / ")

        .append(recommendedStarter.sourceRef())
        .append("\n");
    notesBuilder.append("Reason: ").append(recommendedStarter.reason());
    if (!starterSelection.alternatives().isEmpty()) {
      notesBuilder.append("\n\nAlternatives:\n");
      starterSelection.alternatives().forEach(alternative -> notesBuilder
          .append("- ")
          .append(alternative.key())
          .append(" (")
          .append(alternative.title())
          .append(") - ")
          .append(alternative.reason())
          .append("\n"));
    }
    return notesBuilder.toString().trim();
  }

  private List<CanonicalAgent> fallbackAgents(
      final CanonicalBlueprint currentPlan,
      final AgentGenerationPass agentGeneration) {
    if (agentGeneration.agents().isEmpty()) {
      return currentPlan.agents();
    }
    return agentGeneration.agents();
  }

  private ArchitectureFramingPass toArchitectureFraming(final CanonicalBlueprint currentPlan) {
    return new ArchitectureFramingPass(
        currentPlan.projectName(),
        currentPlan.executiveSummary(),
        currentPlan.architectureOverview(),
        currentPlan.systemContext(),
        currentPlan.majorArchitectureDecisions(),
        currentPlan.nonFunctionalRequirements(),
        currentPlan.crossCuttingConcerns(),
        currentPlan.glossary(),
        currentPlan.workstreams().stream()
            .map(workstream -> new WorkstreamSeed(workstream.title(), workstream.goal(), workstream.whyItExists()))
            .toList());
  }

  private void capturePassUsage(
      final GenerationUsageContext generationUsageContext,
      final ResolvedAiExecutionRoute executionRoute,
      final PassInvocation invocation,
      final AiUsageEventStatus status,
      final String passName,
      final int attempt,
      final Map<String, Object> extraMetadata) {
    if (aiUsageCaptureService == null
        || generationUsageContext == null
        || !generationUsageContext.captureEnabled()
        || executionRoute == null
        || invocation == null) {
      return;
    }

    try {
      aiUsageCaptureService.capture(
          generationUsageContext.userId(),
          generationUsageContext.correlationId(),
          AiUsageSurface.GENERATION,
          status,
          executionRoute,
          invocation.prompt(),
          invocation.rawJson(),
          invocation.response(),
          invocation.startedAt(),
          invocation.completedAt(),
          buildUsageMetadata(generationUsageContext, passName, attempt, extraMetadata));
    } catch (final RuntimeException captureException) {
      log.warn("Failed to capture generation AI usage: passName={}, attempt={}, reason={}",
          passName,
          attempt,
          captureException.getMessage());
    }
  }

  private void capturePassFailure(
      final GenerationUsageContext generationUsageContext,
      final ResolvedAiExecutionRoute executionRoute,
      final Prompt prompt,
      final ChatResponse response,
      final String rawJson,
      final OffsetDateTime startedAt,
      final String passName,
      final int attempt,
      final RuntimeException exception) {
    if (aiUsageCaptureService == null
        || generationUsageContext == null
        || !generationUsageContext.captureEnabled()
        || executionRoute == null
        || prompt == null
        || startedAt == null) {
      return;
    }

    try {
      aiUsageCaptureService.capture(
          generationUsageContext.userId(),
          generationUsageContext.correlationId(),
          AiUsageSurface.GENERATION,
          AiUsageEventStatus.FAILED,
          executionRoute,
          prompt,
          rawJson,
          response,
          startedAt,
          OffsetDateTime.now(),
          buildUsageMetadata(
              generationUsageContext,
              passName,
              attempt,
              Map.of(
                  "failureReason", exception.getClass().getSimpleName(),
                  "failureMessage", exception.getMessage() == null ? "unknown" : exception.getMessage())));
    } catch (final RuntimeException captureException) {
      log.warn("Failed to capture generation AI usage failure: passName={}, attempt={}, reason={}",
          passName,
          attempt,
          captureException.getMessage());
    }
  }

  private Map<String, Object> buildUsageMetadata(
      final GenerationUsageContext generationUsageContext,
      final String passName,
      final int attempt,
      final Map<String, Object> extraMetadata) {
    final Map<String, Object> metadata = new LinkedHashMap<>();
    if (generationUsageContext != null) {
      metadata.putAll(generationUsageContext.baseMetadata());
    }
    metadata.put("operation", "multi_pass_blueprint_generation");
    metadata.put("passName", passName);
    metadata.put("passAttempt", attempt);
    if (extraMetadata != null && !extraMetadata.isEmpty()) {
      metadata.putAll(extraMetadata);
    }
    return Map.copyOf(metadata);
  }

  private Integer providerReportedTotalTokens(final ChatResponse response) {
    if (response == null || response.getMetadata() == null) {
      return null;
    }
    final Usage usage = response.getMetadata().getUsage();
    if (usage == null) {
      return null;
    }
    final Integer totalTokens = usage.getTotalTokens();
    if (totalTokens != null && totalTokens > 0) {
      return totalTokens;
    }
    final Integer promptTokens = usage.getPromptTokens();
    final Integer completionTokens = usage.getCompletionTokens();
    if ((promptTokens == null || promptTokens <= 0) && (completionTokens == null || completionTokens <= 0)) {
      return null;
    }
    return Math.max(0, (promptTokens == null ? 0 : promptTokens) + (completionTokens == null ? 0 : completionTokens));
  }

  private int safeLength(final String value) {
    return value == null ? 0 : value.length();
  }

  private record PassInvocation(
      Prompt prompt,
      ChatResponse response,
      String rawJson,
      OffsetDateTime startedAt,
      OffsetDateTime completedAt) {
  }

  private record PhaseExpansionBatch(
      int batchNumber,
      int batchCount,
      int requiredPhaseFloor,
      List<CanonicalWorkstream> workstreams) {
  }

  private record PhaseCoverageValidation(
      List<String> missingWorkstreams,
      List<String> workstreamsWithoutPhases,
      int totalPhases) {

    private boolean hasWorkstreamCoverageGap() {
      return !missingWorkstreams.isEmpty() || !workstreamsWithoutPhases.isEmpty();
    }

    private List<String> workstreamsMissingCoverage() {
      final List<String> workstreamsMissingCoverage = new ArrayList<>(missingWorkstreams.size()
          + workstreamsWithoutPhases.size());
      workstreamsMissingCoverage.addAll(missingWorkstreams);
      workstreamsMissingCoverage.addAll(workstreamsWithoutPhases);
      return List.copyOf(workstreamsMissingCoverage);
    }
  }
}
