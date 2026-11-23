package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.abstracts.PromptAnalyzerService;
import com.arslanca.orchestrai.business.abstracts.AiExecutionRouteResolverService;
import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.business.prompt.PromptTemplates;
import com.arslanca.orchestrai.business.prompt.SystemPrompts;
import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;
import com.arslanca.orchestrai.core.exception.type.PromptRejectionException;
import com.arslanca.orchestrai.core.util.LlmResponseSanitizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.MDC;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;

@Service
public class PromptAnalyzerManager implements PromptAnalyzerService {

    private static final Logger log = LoggerFactory.getLogger(PromptAnalyzerManager.class);
    private static final String CORRELATION_ID_KEY = "correlationId";

    private final AiExecutionRouteResolverService aiExecutionRouteResolverService;
    private final AiUsageCaptureService aiUsageCaptureService;
    private final ObjectMapper objectMapper;

    public PromptAnalyzerManager(
            final AiExecutionRouteResolverService aiExecutionRouteResolverService,
            final AiUsageCaptureService aiUsageCaptureService,
            final ObjectMapper objectMapper) {
        this.aiExecutionRouteResolverService = aiExecutionRouteResolverService;
        this.aiUsageCaptureService = aiUsageCaptureService;
        this.objectMapper = objectMapper;
    }


    @Override
    public StructuredAnalysis analyzePrompt(final UUID userId, final String userPrompt) {
        log.info("Stage 1: Prompt analysis starting");

        final var systemMessage = new SystemMessage(SystemPrompts.ROUTER_ANALYZER);
        final var userMessage = new UserMessage(PromptTemplates.routerUserPrompt(userPrompt));

        final var prompt = new Prompt(List.of(systemMessage, userMessage));
        final ResolvedAiExecutionRoute route = resolveRoute(userId, "prompt analysis");
        final OffsetDateTime startedAt = OffsetDateTime.now();
        org.springframework.ai.chat.model.ChatResponse response = null;
        String rawJson = null;
        try {
            response = route.chatModel().call(prompt);
            rawJson = response.getResult().getOutput().getText();
            final StructuredAnalysis analysis = parseAndValidateAnalysis(rawJson);
            captureUsage(userId, route, prompt, rawJson, response, startedAt, "prompt-analysis");
            return analysis;
        } catch (RuntimeException exception) {
            captureFailureUsage(userId, route, prompt, rawJson, response, startedAt, "prompt-analysis", exception);
            throw exception;
        }
    }

    @Override
    public StructuredAnalysis analyzePlanningConversation(final UUID userId, final String planningConversationSummary) {
        log.info("Stage 1: Planning conversation analysis starting");

        final var systemMessage = new SystemMessage(SystemPrompts.CHAT_PREPARATION_ANALYZER);
        final var userMessage = new UserMessage(PromptTemplates.chatPreparationAnalysisPrompt(planningConversationSummary));

        final var prompt = new Prompt(List.of(systemMessage, userMessage));
        final ResolvedAiExecutionRoute route = resolveRoute(userId, "planning conversation analysis");
        final OffsetDateTime startedAt = OffsetDateTime.now();
        org.springframework.ai.chat.model.ChatResponse response = null;
        String rawJson = null;
        try {
            response = route.chatModel().call(prompt);
            rawJson = response.getResult().getOutput().getText();
            final StructuredAnalysis analysis = parseAndValidateAnalysis(rawJson);
            captureUsage(userId, route, prompt, rawJson, response, startedAt, "planning-conversation-analysis");
            return analysis;
        } catch (RuntimeException exception) {
            captureFailureUsage(userId, route, prompt, rawJson, response, startedAt, "planning-conversation-analysis", exception);
            throw exception;
        }
    }

    @Override
    public ChatPlanningSnapshot refreshChatPlanningSnapshot(
            final UUID userId,
            final Optional<ChatPlanningSnapshot> previousSnapshot,
            final String recentMessages) {
        log.info("Stage 1: Chat planning snapshot refresh starting");

        final var systemMessage = new SystemMessage(SystemPrompts.CHAT_PLANNING_SNAPSHOT_UPDATER);
        final var userMessage = new UserMessage(PromptTemplates.chatPlanningSnapshotPrompt(previousSnapshot, recentMessages));

        final var prompt = new Prompt(List.of(systemMessage, userMessage));
        final ResolvedAiExecutionRoute route = resolveRoute(userId, "planning snapshot refresh");
        final OffsetDateTime startedAt = OffsetDateTime.now();
        org.springframework.ai.chat.model.ChatResponse response = null;
        String rawJson = null;
        try {
            response = route.chatModel().call(prompt);
            rawJson = response.getResult().getOutput().getText();
            final ChatPlanningSnapshot snapshot = parseAndValidateSnapshot(rawJson);
            captureUsage(userId, route, prompt, rawJson, response, startedAt, "planning-snapshot-refresh");
            return snapshot;
        } catch (RuntimeException exception) {
            captureFailureUsage(userId, route, prompt, rawJson, response, startedAt, "planning-snapshot-refresh", exception);
            throw exception;
        }
    }

    private ResolvedAiExecutionRoute resolveRoute(final UUID userId, final String operationName) {
        final ResolvedAiExecutionRoute route = aiExecutionRouteResolverService.resolve(userId, null, null);
        log.info("Stage 1 route resolved for {}: executionClass={}, model={}",
                operationName,
                route.executionClass(),
                routeLabel(route));
        return route;
    }

    private String routeLabel(final ResolvedAiExecutionRoute route) {
        if (route == null || route.runtimeDescriptor() == null) {
            return "platform-light";
        }
        return route.runtimeDescriptor().providerKey() + ":" + route.runtimeDescriptor().modelKey();
    }

    private StructuredAnalysis parseAndValidateAnalysis(final String rawJson) {
        try {
            final StructuredAnalysis analysis = objectMapper.readValue(
                    LlmResponseSanitizer.extractJson(rawJson), StructuredAnalysis.class);

            if (!analysis.isSafe()) {
                log.warn("LLM safety gate rejected prompt. Reason (internal): {}",
                        analysis.maliciousReason());
                throw new PromptRejectionException("Your request could not be processed.");
            }

            log.info("Stage 1 completed: project={}, complexity={}",
                    analysis.projectName(), analysis.complexity());

            return analysis;

        } catch (final PromptRejectionException ex) {
            throw ex;
        } catch (final Exception ex) {
            log.error("Router JSON parse error: {}", ex.getMessage());
            throw new LlmResponseParseException("Failed to parse LLM analysis output", ex);
        }
    }

    private ChatPlanningSnapshot parseAndValidateSnapshot(final String rawJson) {
        try {
            final ChatPlanningSnapshot snapshot = objectMapper.readValue(
                    LlmResponseSanitizer.extractJson(rawJson), ChatPlanningSnapshot.class);
            return snapshot;
        } catch (final PromptRejectionException ex) {
            throw ex;
        } catch (final LlmResponseParseException ex) {
            throw ex;
        } catch (final Exception ex) {
            log.error("Chat planning snapshot parse error: {}", ex.getMessage());
            throw new LlmResponseParseException("Failed to parse chat planning snapshot output", ex);
        }
    }

    private void captureUsage(
            final UUID userId,
            final ResolvedAiExecutionRoute route,
            final Prompt prompt,
            final String rawJson,
            final org.springframework.ai.chat.model.ChatResponse response,
            final OffsetDateTime startedAt,
            final String operationName) {
        try {
            aiUsageCaptureService.capture(
                    userId,
                    MDC.get(CORRELATION_ID_KEY),
                    AiUsageSurface.PLANNING,
                    AiUsageEventStatus.COMPLETED,
                    route,
                    prompt,
                    rawJson,
                    response,
                    startedAt,
                    OffsetDateTime.now(),
                    Map.of("operation", operationName));
        } catch (RuntimeException captureException) {
            log.warn("Failed to capture planning AI usage: operation={}, reason={}", operationName, captureException.getMessage());
        }
    }

    private void captureFailureUsage(
            final UUID userId,
            final ResolvedAiExecutionRoute route,
            final Prompt prompt,
            final String rawJson,
            final org.springframework.ai.chat.model.ChatResponse response,
            final OffsetDateTime startedAt,
            final String operationName,
            final RuntimeException exception) {
        try {
            aiUsageCaptureService.capture(
                    userId,
                    MDC.get(CORRELATION_ID_KEY),
                    AiUsageSurface.PLANNING,
                    AiUsageEventStatus.FAILED,
                    route,
                    prompt,
                    rawJson,
                    response,
                    startedAt,
                    OffsetDateTime.now(),
                    Map.of(
                            "operation", operationName,
                            "failureReason", exception.getClass().getSimpleName(),
                            "failureMessage", exception.getMessage() == null ? "unknown" : exception.getMessage()));
        } catch (RuntimeException captureException) {
            log.warn("Failed to capture planning AI failure usage: operation={}, reason={}", operationName, captureException.getMessage());
        }
    }
}
