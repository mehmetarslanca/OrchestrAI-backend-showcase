package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.abstracts.BlueprintGeneratorService;
import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.business.prompt.PromptTemplates;
import com.arslanca.orchestrai.business.prompt.SystemPrompts;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.business.runtime.ResolvedHeavyModelRuntimeBinding;
import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;
import com.arslanca.orchestrai.core.util.LlmResponseSanitizer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;

@Service
public class BlueprintGeneratorManager implements BlueprintGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(BlueprintGeneratorManager.class);

    private final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;
    private final ObjectMapper objectMapper;
    private final PlanningTelemetryService planningTelemetryService;
    private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;
    private final AiUsageCaptureService aiUsageCaptureService;

    @Autowired
    public BlueprintGeneratorManager(
            final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager,
            final ObjectMapper objectMapper,
            final PlanningTelemetryService planningTelemetryService,
            final DeterministicPlanningPolicyService deterministicPlanningPolicyService,
            final AiUsageCaptureService aiUsageCaptureService) {
        this.resolvedHeavyModelRuntimeBindingManager = resolvedHeavyModelRuntimeBindingManager;
        this.objectMapper = objectMapper;
        this.planningTelemetryService = planningTelemetryService;
        this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
        this.aiUsageCaptureService = aiUsageCaptureService;
    }

    BlueprintGeneratorManager(
            final ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager,
            final ObjectMapper objectMapper,
            final PlanningTelemetryService planningTelemetryService,
            final DeterministicPlanningPolicyService deterministicPlanningPolicyService) {
        this(
                resolvedHeavyModelRuntimeBindingManager,
                objectMapper,
                planningTelemetryService,
                deterministicPlanningPolicyService,
                null);
    }


    @Override
    public CanonicalBlueprint generateBlueprint(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection) {
        return generateBlueprint(analysis, ideTarget, resolvedHeavyModel, starterSelection, GenerationUsageContext.empty());
    }

    @Override
    public CanonicalBlueprint generateBlueprint(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final GenerationUsageContext generationUsageContext) {
        log.info("Stage 2: Blueprint planning starting - project={}, ide={}, heavyModel={}",
                analysis.projectName(), ideTarget, resolvedHeavyModel.modelKey());

        final ResolvedHeavyModelRuntimeBinding runtimeBinding = resolvedHeavyModelRuntimeBindingManager.bind(resolvedHeavyModel);
        final ChatModel heavyChatModel = runtimeBinding.buildChatModel();
        final ResolvedAiExecutionRoute executionRoute = runtimeBinding.toExecutionRoute(
                resolvedHeavyModel.executionClass(),
                heavyChatModel);
        final DeterministicPlanningProfile planningProfile = deterministicPlanningPolicyService.deriveProfile(analysis);
        final String userPrompt = PromptTemplates.architectUserPrompt(
                planningProfile,
                analysis,
                ideTarget,
                starterSelection);

        final var systemMessage = new SystemMessage(SystemPrompts.CHIEF_ARCHITECT);
        final var userMessage = new UserMessage(userPrompt);

        final var prompt = new Prompt(List.of(systemMessage, userMessage));
        final OffsetDateTime startedAt = OffsetDateTime.now();
        final long startedAtNanos = System.nanoTime();
        ChatResponse response = null;
        String rawJson = null;

        try {
            response = heavyChatModel.call(prompt);
            rawJson = response.getResult().getOutput().getText();
            planningTelemetryService.recordPassExecution(
                    "single_pass_blueprint",
                    analysis.planningComplexity(),
                    PlannerModelRef.fromResolvedHeavyModel(resolvedHeavyModel),
                    providerReportedTotalTokens(response),
                    safeLength(SystemPrompts.CHIEF_ARCHITECT) + safeLength(userPrompt),
                    safeLength(rawJson),
                    (System.nanoTime() - startedAtNanos) / 1_000_000L);

            log.debug("Architect raw response length: {}", rawJson == null ? 0 : rawJson.length());
        } catch (final RuntimeException ex) {
            captureFailureUsage(generationUsageContext, executionRoute, prompt, rawJson, response, startedAt, ex,
                    "single_pass_blueprint");
            throw ex;
        }

        try {
            final CanonicalBlueprint blueprint = objectMapper.readValue(
                    LlmResponseSanitizer.extractJson(rawJson), CanonicalBlueprint.class);
            final CanonicalBlueprint mergedBlueprint = new CanonicalBlueprint(
                    blueprint.projectName(),
                    blueprint.executiveSummary(),
                    blueprint.architectureOverview(),
                    blueprint.systemContext(),
                    blueprint.majorArchitectureDecisions(),
                    blueprint.workstreams(),
                    blueprint.nonFunctionalRequirements(),
                    blueprint.crossCuttingConcerns(),
                    blueprint.glossary(),
                    blueprint.assumptions().isEmpty() ? analysis.assumptions() : blueprint.assumptions(),
                    blueprint.unknowns().isEmpty() ? analysis.unknowns() : blueprint.unknowns(),
                    blueprint.phases(),
                    blueprint.agents(),
                    blueprint.ideRules(),
                    mergeStarterNotes(blueprint.starterNotes(), starterSelection),
                    CanonicalBlueprint.CURRENT_CONTRACT_VERSION);

            log.info("Stage 2 completed: phases={}, agents={}, starterNotesPresent={}",
                    mergedBlueprint.phases().size(),
                    mergedBlueprint.agents().size(),
                    !mergedBlueprint.starterNotes().isBlank());

            captureUsage(
                    generationUsageContext,
                    executionRoute,
                    prompt,
                    rawJson,
                    response,
                    startedAt,
                    AiUsageEventStatus.COMPLETED,
                    buildUsageMetadata(generationUsageContext, "single_pass_blueprint", null));

            return mergedBlueprint;

        } catch (final LlmResponseParseException ex) {
            captureFailureUsage(generationUsageContext, executionRoute, prompt, rawJson, response, startedAt, ex,
                    "single_pass_blueprint");
            throw ex;
        } catch (final Exception ex) {
            captureFailureUsage(generationUsageContext, executionRoute, prompt, rawJson, response, startedAt, ex,
                    "single_pass_blueprint");
            log.error("Architect JSON parse error: {}", ex.getMessage());
            throw new LlmResponseParseException("Failed to parse blueprint output", ex);
        }
    }

    private void captureUsage(
            final GenerationUsageContext generationUsageContext,
            final ResolvedAiExecutionRoute executionRoute,
            final Prompt prompt,
            final String rawJson,
            final ChatResponse response,
            final OffsetDateTime startedAt,
            final AiUsageEventStatus status,
            final Map<String, Object> metadata) {
        if (aiUsageCaptureService == null || generationUsageContext == null || !generationUsageContext.captureEnabled()) {
            return;
        }
        try {
            aiUsageCaptureService.capture(
                    generationUsageContext.userId(),
                    generationUsageContext.correlationId(),
                    AiUsageSurface.GENERATION,
                    status,
                    executionRoute,
                    prompt,
                    rawJson,
                    response,
                    startedAt,
                    OffsetDateTime.now(),
                    metadata);
        } catch (final RuntimeException captureException) {
            log.warn("Failed to capture generation AI usage: operation={}, reason={}",
                    metadata.getOrDefault("operation", "single_pass_blueprint"),
                    captureException.getMessage());
        }
    }

    private void captureFailureUsage(
            final GenerationUsageContext generationUsageContext,
            final ResolvedAiExecutionRoute executionRoute,
            final Prompt prompt,
            final String rawJson,
            final ChatResponse response,
            final OffsetDateTime startedAt,
            final Exception exception,
            final String operation) {
        final Map<String, Object> metadata = buildUsageMetadata(
                generationUsageContext,
                operation,
                Map.of(
                "failureReason", exception.getClass().getSimpleName(),
                "failureMessage", exception.getMessage() == null ? "unknown" : exception.getMessage()));
        captureUsage(
                generationUsageContext,
                executionRoute,
                prompt,
                rawJson,
                response,
                startedAt,
                AiUsageEventStatus.FAILED,
                metadata);
    }

    private Map<String, Object> buildUsageMetadata(
            final GenerationUsageContext generationUsageContext,
            final String operation,
            final Map<String, Object> extraMetadata) {
        final Map<String, Object> metadata = new LinkedHashMap<>();
        if (generationUsageContext != null) {
            metadata.putAll(generationUsageContext.baseMetadata());
        }
        metadata.put("operation", operation);
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

    private String mergeStarterNotes(final String aiStarterNotes, final StarterSelection starterSelection) {
        final String deterministicNotes = buildDeterministicStarterNotes(starterSelection);
        final String normalizedAiStarterNotes = aiStarterNotes == null ? "" : aiStarterNotes.trim();

        if (normalizedAiStarterNotes.isBlank()) {
            return deterministicNotes;
        }
        return deterministicNotes + "\n\n## AI Planning Notes\n\n" + normalizedAiStarterNotes;
    }

    private String buildDeterministicStarterNotes(final StarterSelection starterSelection) {
        final StringBuilder notesBuilder = new StringBuilder();
        if (starterSelection.docsOnlyFallback()) {
            notesBuilder.append("## Starter Resolution\n\n");
            notesBuilder.append("Mode: docs-only\n\n");
            notesBuilder.append("Reason: ").append(starterSelection.docsOnlyFallbackReason());
            return notesBuilder.toString();
        }

        final StarterCandidate recommendedStarter = starterSelection.recommendedStarter();
        notesBuilder.append("## Starter Resolution\n\n");
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

    private int safeLength(final String value) {
        return value == null ? 0 : value.length();
    }

}
