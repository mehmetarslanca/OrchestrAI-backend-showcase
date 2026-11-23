package com.arslanca.orchestrai.business.concretes;

import java.util.List;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelUserPreferenceService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.PromptAnalyzerService;
import com.arslanca.orchestrai.business.abstracts.StarterResolutionService;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary;
import com.arslanca.orchestrai.business.dtos.orchestration.PreviewReuseDisposition;
import com.arslanca.orchestrai.business.dtos.orchestration.RequirementCompletenessReport;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatGenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatPlanningStatusResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationSourceChannel;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.enums.ChatPlanningHandoffState;
import com.arslanca.orchestrai.business.enums.ChatPlanningReadinessStatus;
import com.arslanca.orchestrai.business.planning.preparation.ChatPlanningEvaluationResult;
import com.arslanca.orchestrai.business.planning.preparation.ChatPreparationContextService;
import com.arslanca.orchestrai.business.planning.preparation.PlanningSummaryFactory;
import com.arslanca.orchestrai.business.planning.preparation.PreparationSnapshotCodec;
import com.arslanca.orchestrai.business.planning.preparation.RequirementCompletenessPolicy;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.core.util.PromptInputValidator;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import com.arslanca.orchestrai.entity.concretes.MessageEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.enums.MessageRole;
import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;

@Service
public class ProjectGenerationPreparationManager implements ProjectGenerationPreparationService {

  private static final Logger log = LoggerFactory.getLogger(ProjectGenerationPreparationManager.class);
  private static final String CHAT_NOT_READY_FALLBACK_MESSAGE =
      "Project requirements are not ready for generation review yet.";

  private final PromptAnalyzerService promptAnalyzerService;
  private final StarterResolutionService starterResolutionService;
  private final HeavyModelResolutionService heavyModelResolutionService;
  private final HeavyModelUserPreferenceService heavyModelUserPreferenceService;
  private final RequirementCompletenessPolicy requirementCompletenessPolicy;
  private final PlanningSummaryFactory planningSummaryFactory;
  private final PreparationSnapshotCodec preparationSnapshotCodec;
  private final ChatPreparationContextService chatPreparationContextService;
  private final ProjectGenerationPreparationRepository preparationRepository;
  private final MeterRegistry meterRegistry;
  private final TaskExecutor taskExecutor;

  public ProjectGenerationPreparationManager(
      final PromptAnalyzerService promptAnalyzerService,
      final StarterResolutionService starterResolutionService,
      final HeavyModelResolutionService heavyModelResolutionService,
      final HeavyModelUserPreferenceService heavyModelUserPreferenceService,
      final RequirementCompletenessPolicy requirementCompletenessPolicy,
      final PlanningSummaryFactory planningSummaryFactory,
      final PreparationSnapshotCodec preparationSnapshotCodec,
      final ChatPreparationContextService chatPreparationContextService,
      final ProjectGenerationPreparationRepository preparationRepository,
      final MeterRegistry meterRegistry,
      @Qualifier("applicationTaskExecutor") final ObjectProvider<TaskExecutor> applicationTaskExecutorProvider,
      final ObjectProvider<TaskExecutor> genericTaskExecutorProvider) {
    this.promptAnalyzerService = promptAnalyzerService;
    this.starterResolutionService = starterResolutionService;
    this.heavyModelResolutionService = heavyModelResolutionService;
    this.heavyModelUserPreferenceService = heavyModelUserPreferenceService;
    this.requirementCompletenessPolicy = requirementCompletenessPolicy;
    this.planningSummaryFactory = planningSummaryFactory;
    this.preparationSnapshotCodec = preparationSnapshotCodec;
    this.chatPreparationContextService = chatPreparationContextService;
    this.preparationRepository = preparationRepository;
    this.meterRegistry = meterRegistry;
    TaskExecutor resolvedTaskExecutor = applicationTaskExecutorProvider.getIfAvailable();
    if (resolvedTaskExecutor == null) {
      resolvedTaskExecutor = genericTaskExecutorProvider.getIfAvailable();
    }
    if (resolvedTaskExecutor == null) {
      throw new IllegalStateException("No TaskExecutor bean available in application context.");
    }
    this.taskExecutor = resolvedTaskExecutor;
  }

  @Override
  @Transactional
  public GenerationPreparationResponse prepareGeneration(
      final UUID userId,
      final GenerationPreparationRequest request) {
    final String sourceChannel = request.resolvedSourceChannel().name();
    recordPreparationMetric(sourceChannel, "requested", "request_received");

    try {
      final String sanitizedPrompt = PromptInputValidator.validateAndSanitize(request.prompt());
      final String normalizedProjectName = request.projectName().trim();
      final String normalizedIdeTarget = normalizeIdeTarget(request.ideTarget());
      final StructuredAnalysis analysis = promptAnalyzerService.analyzePrompt(userId, sanitizedPrompt);
      final RequirementCompletenessReport completenessReport = requirementCompletenessPolicy
          .evaluateRequirementCompleteness(analysis);

      requirementCompletenessPolicy.assertPreparationReady(completenessReport);

      final StarterSelection starterSelection = starterResolutionService.resolveStarter(analysis, normalizedIdeTarget);
      final ResolvedHeavyModel resolvedHeavyModel = resolvePreparationHeavyModel(userId);
      final PlanningSummary planningSummary = planningSummaryFactory.buildPlanningSummary(
          analysis,
          starterSelection,
          resolvedHeavyModel);
      final ProjectGenerationPreparationEntity preparation = preparationRepository.save(
          ProjectGenerationPreparationEntity.create(
              userId,
              null,
              null,
              sourceChannel,
              normalizedProjectName,
              normalizedIdeTarget,
              sanitizedPrompt,
              preparationSnapshotCodec.toPayload(analysis),
              preparationSnapshotCodec.toPayload(starterSelection),
              analysis.contractVersion(),
              preparationSnapshotCodec.buildPreparationSnapshot(
                  analysis,
                  planningSummary,
                  starterSelection,
                  completenessReport,
                  resolvedHeavyModel,
                  java.util.Optional.empty(),
                  null)));

      log.info(
          "Generation preparation persisted: preparationId={}, userId={}, sourceChannel={}, projectName={}, ideTarget={}, starterKey={}",
          preparation.getId(),
          userId,
          sourceChannel,
          normalizedProjectName,
          normalizedIdeTarget,
          resolveStarterLogValue(starterSelection));
      recordPreparationMetric(
          sourceChannel,
          "success",
          starterSelection.docsOnlyFallback() ? "docs_only_fallback" : "prepared");

      return toResponse(preparation.getId(), normalizedProjectName, normalizedIdeTarget, starterSelection,
          planningSummary);
    } catch (final IllegalArgumentException exception) {
      recordPreparationMetric(sourceChannel, "failed", "validation_failure");
      throw exception;
    } catch (final RuntimeException exception) {
      recordPreparationMetric(sourceChannel, "failed", "preparation_failed");
      throw exception;
    }
  }

  @Override
  @Transactional
  public GenerationPreparationResponse prepareGenerationFromChat(
      final UUID userId,
      final UUID chatId,
      final ChatGenerationPreparationRequest request) {
    final String sourceChannel = GenerationSourceChannel.CHAT.name();
    recordPreparationMetric(sourceChannel, "requested", "request_received");

    try {
      final String normalizedIdeTarget = normalizeIdeTarget(request.ideTarget());
      final ChatEntity chat = chatPreparationContextService.loadOwnedChat(userId, chatId);
      final long currentMessageCount = chatPreparationContextService.countMessages(chatId);
      final GenerationPreparationResponse reusablePreparation = findPreparedResponse(
          userId,
          chatId,
          currentMessageCount,
          normalizedIdeTarget);
      if (reusablePreparation != null) {
        recordPreparationMetric(
            sourceChannel,
            "success",
            reusablePreparation.docsOnlyFallback() ? "docs_only_fallback" : "prepared");
        return reusablePreparation;
      }

      final ProjectGenerationPreparationEntity currentPreparedPreparation = findCurrentPreparedEntity(
          userId,
          chatId,
          currentMessageCount);
      if (currentPreparedPreparation != null) {
        final GenerationPreparationResponse clonedPreparation = createPreparationFromExistingPreparation(
            userId,
            chat,
            currentMessageCount,
            normalizedIdeTarget,
            currentPreparedPreparation);
        recordPreparationMetric(
            sourceChannel,
            "success",
            clonedPreparation.docsOnlyFallback() ? "docs_only_fallback" : "prepared");
        return clonedPreparation;
      }

      final ChatPlanningStatusResponse planningStatus = getChatPlanningStatus(userId, chatId);
      throw buildPreparationBlockedException(planningStatus);
    } catch (final ResourceNotFoundException exception) {
      recordPreparationMetric(sourceChannel, "failed", "chat_not_found");
      throw exception;
    } catch (final IllegalArgumentException exception) {
      recordPreparationMetric(sourceChannel, "failed", "validation_failure");
      throw exception;
    } catch (final RuntimeException exception) {
      recordPreparationMetric(sourceChannel, "failed", "preparation_failed");
      throw exception;
    }
  }

  @Override
  public ChatPlanningStatusResponse getChatPlanningStatus(final UUID userId, final UUID chatId) {
    final ChatEntity chat = chatPreparationContextService.loadOwnedChat(userId, chatId);
    final List<MessageEntity> recentMessages = chatPreparationContextService.loadRecentMessages(chatId);

    if (!hasCompletedAssistantTurn(recentMessages)) {
      return ChatPlanningStatusResponse.notReady(
          ChatPlanningReadinessStatus.DISCOVERY_IN_PROGRESS,
          "Waiting for the architect to finish the current planning turn.",
          null);
    }

    final long currentMessageCount = chatPreparationContextService.countMessages(chatId);
    final ChatPlanningEvaluationResult evaluationResult = chatPreparationContextService.readPlanningEvaluation(
        chatId,
        currentMessageCount);

    if (evaluationResult.status() == ChatPlanningReadinessStatus.READY_FOR_PREPARATION) {
      final GenerationPreparationResponse currentPreparedResponse = findCurrentPreparedResponse(userId, chatId,
          currentMessageCount);
      return ChatPlanningStatusResponse.ready(evaluationResult.reason(), currentPreparedResponse);
    }

    if (evaluationResult.status() == ChatPlanningReadinessStatus.ANALYZING) {
      return ChatPlanningStatusResponse.analyzing(evaluationResult.reason());
    }

    return ChatPlanningStatusResponse.notReady(
        evaluationResult.status(),
        evaluationResult.reason(),
        null);
  }

  @Override
  public ChatPlanningStatusResponse evaluateChatPlanningAfterAssistantTurn(
      final UUID userId,
      final UUID chatId,
      final String ideTarget) {
    return queueChatPlanningAnalysis(userId, chatId, normalizeIdeTarget(ideTarget), false);
  }

  @Override
  public ChatPlanningStatusResponse retryChatPlanningAfterAssistantTurn(
      final UUID userId,
      final UUID chatId,
      final String ideTarget) {
    return queueChatPlanningAnalysis(userId, chatId, normalizeIdeTarget(ideTarget), true);
  }

  @Override
  @Transactional
  public void registerToolDrivenPlanningHandoff(
      final UUID userId,
      final UUID chatId,
      final ChatPlanningSnapshot snapshot) {
    final ChatEntity chat = chatPreparationContextService.loadOwnedChat(userId, chatId);
    final long currentMessageCount = chatPreparationContextService.countMessages(chatId);
    chatPreparationContextService.registerToolDrivenPlanningHandoff(chat, currentMessageCount, snapshot);
  }

  private ChatPlanningStatusResponse queueChatPlanningAnalysis(
      final UUID userId,
      final UUID chatId,
      final String normalizedIdeTarget,
      final boolean forceRetry) {
    final ChatEntity chat = chatPreparationContextService.loadOwnedChat(userId, chatId);
    final List<MessageEntity> recentMessages = chatPreparationContextService.loadRecentMessages(chatId);

    if (!hasCompletedAssistantTurn(recentMessages)) {
      return ChatPlanningStatusResponse.notReady(
          ChatPlanningReadinessStatus.DISCOVERY_IN_PROGRESS,
          "Waiting for the architect to finish the current planning turn.",
          null);
    }

    final long currentMessageCount = chatPreparationContextService.countMessages(chatId);
    final ChatPlanningEvaluationResult currentEvaluation = chatPreparationContextService.readPlanningEvaluation(chatId,
        currentMessageCount);

    if (currentEvaluation.status() == ChatPlanningReadinessStatus.ANALYZING) {
      return ChatPlanningStatusResponse.analyzing(currentEvaluation.reason());
    }

    if (currentEvaluation.status() == ChatPlanningReadinessStatus.READY_FOR_PREPARATION) {
      final GenerationPreparationResponse preparedResponse = findCurrentPreparedResponse(userId, chatId,
          currentMessageCount);
      if (preparedResponse != null) {
        return ChatPlanningStatusResponse.ready(currentEvaluation.reason(), preparedResponse);
      }
    }

    chatPreparationContextService.markPlanningAnalysisRequested(
        chat,
        currentMessageCount,
        "Analyzing the latest planning handoff.");
    taskExecutor.execute(() -> analyzeChatPlanningTurn(userId, chatId, normalizedIdeTarget, currentMessageCount));
    return ChatPlanningStatusResponse.analyzing("Analyzing the latest planning handoff.");
  }

  private void analyzeChatPlanningTurn(
      final UUID userId,
      final UUID chatId,
      final String normalizedIdeTarget,
      final long targetMessageCount) {
    try {
      final ChatEntity chat = chatPreparationContextService.loadOwnedChat(userId, chatId);
      if (chatPreparationContextService.countMessages(chatId) != targetMessageCount) {
        log.debug("Skipping stale planning analysis before snapshot refresh: chatId={}, targetMessages={}", chatId,
            targetMessageCount);
        return;
      }

      final ChatPlanningEvaluationResult currentEvaluation = chatPreparationContextService.readPlanningEvaluation(chatId,
          targetMessageCount);
      final ChatPlanningSnapshot snapshot = currentEvaluation.snapshot() != null
          && currentEvaluation.snapshot().handoffState() == ChatPlanningHandoffState.READY_FOR_PREPARATION
          ? currentEvaluation.snapshot()
          : chatPreparationContextService.refreshChatPlanningSnapshot(userId, chat, targetMessageCount);
      if (chatPreparationContextService.countMessages(chatId) != targetMessageCount) {
        log.debug("Skipping stale planning analysis after snapshot refresh: chatId={}, targetMessages={}", chatId,
            targetMessageCount);
        return;
      }

      if (snapshot.handoffState() != ChatPlanningHandoffState.READY_FOR_PREPARATION) {
        chatPreparationContextService.updatePlanningStatusIfCurrent(
            chatId,
            targetMessageCount,
            ChatPlanningReadinessStatus.DISCOVERY_IN_PROGRESS,
            defaultPlanningReason(snapshot.handoffSummary(), "Planning conversation is still being refined."));
        return;
      }

      final List<MessageEntity> recentMessages = chatPreparationContextService.loadRecentMessages(chatId);
      if (!hasCompletedAssistantTurn(recentMessages)
          || chatPreparationContextService.countMessages(chatId) != targetMessageCount) {
        log.debug("Skipping stale planning analysis before structured analysis generation: chatId={}, targetMessages={}",
            chatId,
            targetMessageCount);
        return;
      }

      final String planningConversationSummary = PromptInputValidator.validateAndSanitizeChatContext(
          chatPreparationContextService.buildPreparationPromptSummary(snapshot, recentMessages));
      final StructuredAnalysis analysis = promptAnalyzerService.analyzePlanningConversation(userId, planningConversationSummary);
      final RequirementCompletenessReport completenessReport = requirementCompletenessPolicy
          .evaluateRequirementCompleteness(analysis);

      if (!completenessReport.ready()) {
        log.info(
            "Planning analysis still blocked by completeness gate: chatId={}, messages={}, missing={}, unresolved={}",
            chatId,
            targetMessageCount,
            completenessReport.missingRequiredSignals(),
            completenessReport.unresolvedRequiredSignals());
        chatPreparationContextService.updatePlanningStatusIfCurrent(
            chatId,
            targetMessageCount,
            ChatPlanningReadinessStatus.DISCOVERY_IN_PROGRESS,
            buildCompletenessReason(completenessReport));
        return;
      }

      createOrReuseChatPreparationFromAnalysis(
          userId,
          chat,
          targetMessageCount,
          planningConversationSummary,
          analysis,
          normalizedIdeTarget == null ? "generic" : normalizedIdeTarget);
      chatPreparationContextService.updatePlanningStatusIfCurrent(
          chatId,
          targetMessageCount,
          ChatPlanningReadinessStatus.READY_FOR_PREPARATION,
          defaultPlanningReason(snapshot.handoffSummary(), "Planning conversation is ready for generation review."));
    } catch (final ResourceNotFoundException exception) {
      log.debug("Skipping planning analysis for missing chat: chatId={}, reason={}", chatId, exception.getMessage());
    } catch (final Exception exception) {
      final String reason = buildAnalysisFailureReason(exception);
      chatPreparationContextService.updatePlanningStatusIfCurrent(
          chatId,
          targetMessageCount,
          ChatPlanningReadinessStatus.ANALYSIS_FAILED,
          reason);
      log.warn(
          "Chat planning analysis failed for latest turn: chatId={}, messages={}, reason={}",
          chatId,
          targetMessageCount,
          exception.getMessage());
    }
  }

  private GenerationPreparationResponse createOrReuseChatPreparationFromAnalysis(
      final UUID userId,
      final ChatEntity chat,
      final long messageCount,
      final String sanitizedPromptSummary,
      final StructuredAnalysis analysis,
      final String normalizedIdeTarget) {
    final GenerationPreparationResponse reusablePreparation = findPreparedResponse(
        userId,
        chat.getId(),
        messageCount,
        normalizedIdeTarget);
    if (reusablePreparation != null) {
      return reusablePreparation;
    }

    final RequirementCompletenessReport completenessReport = requirementCompletenessPolicy
        .evaluateRequirementCompleteness(analysis);
    requirementCompletenessPolicy.assertPreparationReady(completenessReport);

    final String projectName = chatPreparationContextService.resolveProjectName(chat, analysis);
    final StarterSelection starterSelection = starterResolutionService.resolveStarter(analysis, normalizedIdeTarget);
    final ResolvedHeavyModel resolvedHeavyModel = resolvePreparationHeavyModel(userId);
    final PlanningSummary planningSummary = planningSummaryFactory.buildPlanningSummary(
        analysis,
        starterSelection,
        resolvedHeavyModel);
    final ProjectGenerationPreparationEntity preparation = preparationRepository.save(
        ProjectGenerationPreparationEntity.create(
            userId,
            chat.getId(),
            messageCount,
            GenerationSourceChannel.CHAT.name(),
            projectName,
            normalizedIdeTarget,
            sanitizedPromptSummary,
            preparationSnapshotCodec.toPayload(analysis),
            preparationSnapshotCodec.toPayload(starterSelection),
            analysis.contractVersion(),
            preparationSnapshotCodec.buildPreparationSnapshot(
                analysis,
                planningSummary,
                starterSelection,
                completenessReport,
                resolvedHeavyModel,
                java.util.Optional.empty(),
                messageCount)));

    log.info(
        "Chat generation preparation persisted: preparationId={}, userId={}, chatId={}, messages={}, ideTarget={}, starterKey={}",
        preparation.getId(),
        userId,
        chat.getId(),
        messageCount,
        normalizedIdeTarget,
        resolveStarterLogValue(starterSelection));

    return toResponse(preparation.getId(), projectName, normalizedIdeTarget, starterSelection, planningSummary);
  }

  private GenerationPreparationResponse createPreparationFromExistingPreparation(
      final UUID userId,
      final ChatEntity chat,
      final long currentMessageCount,
      final String normalizedIdeTarget,
      final ProjectGenerationPreparationEntity existingPreparation) {
    final GenerationPreparationResponse exactPreparation = findPreparedResponse(
        userId,
        chat.getId(),
        currentMessageCount,
        normalizedIdeTarget);
    if (exactPreparation != null) {
      return exactPreparation;
    }

    final StructuredAnalysis analysis = preparationSnapshotCodec.readStructuredAnalysis(existingPreparation);
    final String sanitizedPromptSummary = PromptInputValidator.validateAndSanitizeChatContext(existingPreparation.getPromptSummary());
    return createOrReuseChatPreparationFromAnalysis(
        userId,
        chat,
        currentMessageCount,
        sanitizedPromptSummary,
        analysis,
        normalizedIdeTarget);
  }

  private ProjectGenerationPreparationEntity findCurrentPreparedEntity(
      final UUID userId,
      final UUID chatId,
      final long messageCount) {
    return preparationRepository
        .findFirstByUserIdAndChatIdAndChatMessageCountAndStatusOrderByCreatedAtDesc(
            userId,
            chatId,
            messageCount,
            ProjectGenerationPreparationStatus.PREPARED)
        .orElse(null);
  }

  private GenerationPreparationResponse findCurrentPreparedResponse(
      final UUID userId,
      final UUID chatId,
      final long messageCount) {
    final ProjectGenerationPreparationEntity preparation = findCurrentPreparedEntity(userId, chatId, messageCount);
    return preparation == null ? null : toResponse(preparation);
  }

  private GenerationPreparationResponse findPreparedResponse(
      final UUID userId,
      final UUID chatId,
      final long messageCount,
      final String normalizedIdeTarget) {
    return preparationRepository
        .findFirstByUserIdAndChatIdAndChatMessageCountAndIdeTargetAndStatusOrderByCreatedAtDesc(
            userId,
            chatId,
            messageCount,
            normalizedIdeTarget,
            ProjectGenerationPreparationStatus.PREPARED)
        .map(this::toResponse)
        .orElse(null);
  }

  private boolean hasCompletedAssistantTurn(final List<MessageEntity> recentMessages) {
    if (recentMessages == null || recentMessages.isEmpty()) {
      return false;
    }
    return recentMessages.get(recentMessages.size() - 1).getRole() == MessageRole.ASSISTANT;
  }

  private RuntimeException buildPreparationBlockedException(final ChatPlanningStatusResponse planningStatus) {
    final String message = planningStatus.reason() == null || planningStatus.reason().isBlank()
        ? CHAT_NOT_READY_FALLBACK_MESSAGE
        : planningStatus.reason();
    if (planningStatus.status() == ChatPlanningReadinessStatus.ANALYZING
        || planningStatus.status() == ChatPlanningReadinessStatus.ANALYSIS_FAILED) {
      return new IllegalStateException(message);
    }
    return new IllegalArgumentException(message);
  }

  private GenerationPreparationResponse toResponse(final ProjectGenerationPreparationEntity preparation) {
    if (preparation == null) {
      return null;
    }

    final StarterSelection starterSelection = preparationSnapshotCodec.readStarterSelection(preparation);
    final PlanningSummary planningSummary = preparationSnapshotCodec.readPlanningSummary(preparation);
    return toResponse(
        preparation.getId(),
        preparation.getProjectName(),
        preparation.getIdeTarget(),
        starterSelection,
        planningSummary);
  }

  private GenerationPreparationResponse toResponse(
      final UUID preparationId,
      final String projectName,
      final String ideTarget,
      final StarterSelection starterSelection,
      final PlanningSummary planningSummary) {
    return new GenerationPreparationResponse(
        preparationId,
        projectName,
        ideTarget,
        starterSelection == null ? null : starterSelection.recommendedStarter(),
        starterSelection == null ? List.of() : starterSelection.alternatives(),
        starterSelection != null && starterSelection.docsOnlyFallback(),
        starterSelection == null ? null : starterSelection.docsOnlyFallbackReason(),
        planningSummary);
  }

  private ResolvedHeavyModel resolvePreparationHeavyModel(final UUID userId) {
    try {
      return heavyModelResolutionService.resolve(userId, null, null);
    } catch (final RuntimeException exception) {
      if (requiresStrictPreparationBinding(userId)) {
        throw exception;
      }
      log.warn(
          "Heavy model could not be bound during preparation. Falling back to model-agnostic preparation snapshot: userId={}, reason={}",
          userId,
          exception.getMessage());
      return null;
    }
  }

  private boolean requiresStrictPreparationBinding(final UUID userId) {
    if (userId == null) {
      return false;
    }
    return heavyModelUserPreferenceService.findPreference(userId)
        .map(preference -> preference.authSource() != null && preference.authSource() != CredentialSource.PLATFORM)
        .orElse(false);
  }

  private String buildCompletenessReason(final RequirementCompletenessReport completenessReport) {
    if (completenessReport == null) {
      return CHAT_NOT_READY_FALLBACK_MESSAGE;
    }
    if (!completenessReport.missingRequiredSignals().isEmpty()) {
      return "Planning is still missing required signals: "
          + String.join(", ", completenessReport.missingRequiredSignals());
    }
    if (!completenessReport.unresolvedRequiredSignals().isEmpty()) {
      return "Planning is still resolving required constraints: "
          + String.join(", ", completenessReport.unresolvedRequiredSignals());
    }
    return CHAT_NOT_READY_FALLBACK_MESSAGE;
  }

  private String buildAnalysisFailureReason(final Exception exception) {
    return "The latest planning handoff could not be analyzed. " + exception.getMessage();
  }

  private String defaultPlanningReason(final String value, final String fallback) {
    if (value == null || value.isBlank()) {
      return fallback;
    }
    return value.trim();
  }

  private String resolveStarterLogValue(final StarterSelection starterSelection) {
    if (starterSelection == null) {
      return "unknown";
    }
    if (starterSelection.docsOnlyFallback()) {
      return PreviewReuseDisposition.legacyDocsOnlyStarterKey();
    }
    return sanitizeMetricTag(
        starterSelection.recommendedStarter() == null ? null : starterSelection.recommendedStarter().key());
  }

  private void recordPreparationMetric(final String sourceChannel, final String outcome, final String reason) {
    meterRegistry.counter(
        "project_generation.prepare.request",
        "sourceChannel", sanitizeMetricTag(sourceChannel),
        "outcome", sanitizeMetricTag(outcome),
        "reason", sanitizeMetricTag(reason))
        .increment();
  }

  private String normalizeIdeTarget(final String ideTarget) {
    if (ideTarget == null || ideTarget.isBlank()) {
      return null;
    }
    return ideTarget.trim().toLowerCase();
  }

  private String sanitizeMetricTag(final String value) {
    if (value == null || value.isBlank()) {
      return "unknown";
    }
    return value.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "_");
  }
}
