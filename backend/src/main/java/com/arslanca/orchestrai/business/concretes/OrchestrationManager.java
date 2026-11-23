package com.arslanca.orchestrai.business.concretes;

import java.util.function.BiConsumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.BlueprintPlanningService;
import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.abstracts.OrchestrationService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.abstracts.PromptAnalyzerService;
import com.arslanca.orchestrai.business.abstracts.StarterResolutionService;
import com.arslanca.orchestrai.business.concretes.cancellation.GenerationCancellationToken;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlanningResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStage;
import com.arslanca.orchestrai.business.dtos.projectgeneration.OrchestrationResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.core.util.PromptInputValidator;

@Service
public class OrchestrationManager implements OrchestrationService {

  private static final Logger log = LoggerFactory.getLogger(OrchestrationManager.class);

  private final PromptAnalyzerService promptAnalyzerService;
  private final BlueprintPlanningService blueprintPlanningService;
  private final PlanningModelPolicyService planningModelPolicyService;
  private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;
  private final StarterResolutionService starterResolutionService;

  public OrchestrationManager(
      final PromptAnalyzerService promptAnalyzerService,
      final BlueprintPlanningService blueprintPlanningService,
      final PlanningModelPolicyService planningModelPolicyService,
      final DeterministicPlanningPolicyService deterministicPlanningPolicyService,
      final StarterResolutionService starterResolutionService) {
    this.promptAnalyzerService = promptAnalyzerService;
    this.blueprintPlanningService = blueprintPlanningService;
    this.planningModelPolicyService = planningModelPolicyService;
    this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
    this.starterResolutionService = starterResolutionService;
  }

  @Override
  public OrchestrationResult orchestrate(
      final String userPrompt,
      final String ideTarget,
      final ResolvedHeavyModel resolvedHeavyModel,
      final GenerationCancellationToken cancellationToken,
      final BiConsumer<GenerationStage, String> progressCallback) {
    log.info("Orchestration pipeline starting");

    final String sanitizedPrompt = PromptInputValidator.validateAndSanitize(userPrompt);
    progressCallback.accept(GenerationStage.ANALYZING, "Analyzing project requirements...");

    final StructuredAnalysis analysis = promptAnalyzerService.analyzePrompt(sanitizedPrompt);
    final DeterministicPlanningProfile planningProfile = deterministicPlanningPolicyService.deriveProfile(analysis);
    cancellationToken.throwIfCancellationRequested();

    progressCallback.accept(GenerationStage.MATCHING_STARTER, "Matching the best approved starter...");
    final StarterSelection starterSelection = starterResolutionService.resolveStarter(analysis, ideTarget);
    cancellationToken.throwIfCancellationRequested();

    if (starterSelection.docsOnlyFallback()) {
      progressCallback.accept(GenerationStage.PREPARING_REVIEW,
          "No confident starter matched. Continuing in docs-only planning mode...");
    } else {
      progressCallback.accept(GenerationStage.PREPARING_REVIEW,
          "Starter recommendation prepared. Continuing with blueprint planning...");
    }

    final PlanningModelPolicy planningModelPolicy = planningModelPolicyService.resolvePolicy(
        planningProfile.effectiveComplexity(),
        resolvedHeavyModel,
        starterSelection.docsOnlyFallback());
    progressCallback.accept(GenerationStage.PLANNING_BLUEPRINT,
        planningMessageFor(planningModelPolicy));

    final BlueprintPlanningResult planningResult = blueprintPlanningService.planBlueprint(
        analysis,
        ideTarget,
        resolvedHeavyModel,
        starterSelection,
        planningModelPolicy);
    if (!planningResult.accepted()) {
      throw new IllegalStateException("Generated blueprint did not pass quality gates.");
    }
    final var canonicalBlueprint = planningResult.canonicalBlueprint();

    cancellationToken.throwIfCancellationRequested();
    log.info("Orchestration pipeline completed: project={}", canonicalBlueprint.projectName());
    return new OrchestrationResult(
        canonicalBlueprint,
        starterSelection,
        planningResult.qualitySummary(),
        planningResult.repairStatus());
  }

  @Override
  public OrchestrationResult orchestratePrepared(
      final StructuredAnalysis analysis,
      final String ideTarget,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final GenerationCancellationToken cancellationToken,
      final BiConsumer<GenerationStage, String> progressCallback) {
    return orchestratePrepared(
        analysis,
        ideTarget,
        resolvedHeavyModel,
        starterSelection,
        cancellationToken,
        progressCallback,
        GenerationUsageContext.empty());
  }

  @Override
  public OrchestrationResult orchestratePrepared(
      final StructuredAnalysis analysis,
      final String ideTarget,
      final ResolvedHeavyModel resolvedHeavyModel,
      final StarterSelection starterSelection,
      final GenerationCancellationToken cancellationToken,
      final BiConsumer<GenerationStage, String> progressCallback,
      final GenerationUsageContext generationUsageContext) {
    cancellationToken.throwIfCancellationRequested();

    if (starterSelection.docsOnlyFallback()) {
      progressCallback.accept(GenerationStage.PREPARING_REVIEW,
          "Docs-only fallback confirmed. Continuing with blueprint planning...");
    } else {
      progressCallback.accept(GenerationStage.PREPARING_REVIEW,
          "Starter recommendation confirmed. Continuing with blueprint planning...");
    }

    final DeterministicPlanningProfile planningProfile = deterministicPlanningPolicyService.deriveProfile(analysis);
    final PlanningModelPolicy planningModelPolicy = planningModelPolicyService.resolvePolicy(
        planningProfile.effectiveComplexity(),
        resolvedHeavyModel,
        starterSelection.docsOnlyFallback());
    progressCallback.accept(GenerationStage.PLANNING_BLUEPRINT,
        planningMessageFor(planningModelPolicy));
    final BlueprintPlanningResult planningResult = blueprintPlanningService.planBlueprint(
        analysis,
        ideTarget,
        resolvedHeavyModel,
        starterSelection,
        planningModelPolicy,
        generationUsageContext);
    if (!planningResult.accepted()) {
      throw new IllegalStateException("Generated blueprint did not pass quality gates.");
    }
    final var canonicalBlueprint = planningResult.canonicalBlueprint();

    cancellationToken.throwIfCancellationRequested();
    log.info("Prepared orchestration pipeline completed: project={}", canonicalBlueprint.projectName());
    return new OrchestrationResult(
        canonicalBlueprint,
        starterSelection,
        planningResult.qualitySummary(),
        planningResult.repairStatus());
  }

  private String planningMessageFor(final PlanningModelPolicy planningModelPolicy) {
    if (planningModelPolicy
        .selectedPlanningMode() == com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.SINGLE_PASS) {
      return "Generating plan-only architectural blueprint...";
    }
    if (planningModelPolicy
        .selectedPlanningMode() == com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode.REDUCED_MULTI_PASS) {
      return "Running reduced multi-pass blueprint planning...";
    }
    return "Running multi-pass blueprint planning...";
  }
}
