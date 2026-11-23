package com.arslanca.orchestrai.business.planning.preparation;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.arslanca.orchestrai.business.abstracts.PromptAnalyzerService;
import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.enums.ChatPlanningReadinessStatus;
import com.arslanca.orchestrai.core.exception.type.LlmResponseParseException;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.core.util.PromptInputValidator;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatPlanningSnapshotRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import com.arslanca.orchestrai.entity.concretes.ChatPlanningSnapshotEntity;
import com.arslanca.orchestrai.entity.concretes.MessageEntity;

@Service
public class ChatPreparationContextService {

    private static final Logger log = LoggerFactory.getLogger(ChatPreparationContextService.class);
    private static final int MAX_CONTEXT_MESSAGES = 20;
    private static final int MAX_SNAPSHOT_REFRESH_ATTEMPTS = 2;

    private final ChatPlanningSnapshotRepository snapshotRepository;
    private final ChatRepository chatRepository;
    private final MessageRepository messageRepository;
    private final PromptAnalyzerService promptAnalyzerService;
    private final PreparationSnapshotCodec preparationSnapshotCodec;

    public ChatPreparationContextService(
            final ChatPlanningSnapshotRepository snapshotRepository,
            final ChatRepository chatRepository,
            final MessageRepository messageRepository,
            final PromptAnalyzerService promptAnalyzerService,
            final PreparationSnapshotCodec preparationSnapshotCodec) {
        this.snapshotRepository = snapshotRepository;
        this.chatRepository = chatRepository;
        this.messageRepository = messageRepository;
        this.promptAnalyzerService = promptAnalyzerService;
        this.preparationSnapshotCodec = preparationSnapshotCodec;
    }

    public ChatEntity loadOwnedChat(final UUID userId, final UUID chatId) {
        final Optional<ChatEntity> ownedChatWithProject = chatRepository.findByIdAndUserIdWithProject(chatId, userId);
        final Optional<ChatEntity> safeOwnedChatWithProject = ownedChatWithProject == null ? Optional.empty() : ownedChatWithProject;
        return safeOwnedChatWithProject
                .or(() -> chatRepository.findByIdAndUserId(chatId, userId))
                .orElseThrow(() -> new ResourceNotFoundException("Chat not found or access denied"));
    }

    public long countMessages(final UUID chatId) {
        return messageRepository.countByChatId(chatId);
    }

    public List<MessageEntity> loadRecentMessages(final UUID chatId) {
        final List<MessageEntity> recentMessages = new ArrayList<>(messageRepository.findRecentMessagesByChatId(chatId, MAX_CONTEXT_MESSAGES));
        java.util.Collections.reverse(recentMessages);
        return recentMessages;
    }

    @Transactional
    public void markPlanningAnalysisRequested(
            final ChatEntity chat,
            final long targetMessageCount,
            final String reason) {
        final ChatPlanningSnapshotEntity snapshotEntity = snapshotRepository.findByChatId(chat.getId())
                .orElseGet(() -> ChatPlanningSnapshotEntity.create(
                        chat,
                        0L,
                        ChatPlanningSnapshot.CURRENT_CONTRACT_VERSION,
                        null));

        snapshotEntity.markAnalysisRequested(targetMessageCount, defaultReason(reason, "Planning analysis is running."));
        snapshotRepository.save(snapshotEntity);
    }

    @Transactional
    public void registerToolDrivenPlanningHandoff(
            final ChatEntity chat,
            final long currentMessageCount,
            final ChatPlanningSnapshot snapshot) {
        final ChatPlanningSnapshotEntity snapshotEntity = snapshotRepository.findByChatId(chat.getId())
                .orElseGet(() -> ChatPlanningSnapshotEntity.create(
                        chat,
                        currentMessageCount,
                        snapshot.contractVersion(),
                        preparationSnapshotCodec.toPayload(snapshot)));

        snapshotEntity.updateSnapshot(
                currentMessageCount,
                snapshot.contractVersion(),
                preparationSnapshotCodec.toPayload(snapshot));
        snapshotEntity.updateEvaluation(
                currentMessageCount,
                ChatPlanningReadinessStatus.READY_FOR_PREPARATION,
                "Planning handoff recorded from assistant tool call. "
                        + defaultReason(snapshot.handoffSummary(), "Planning conversation is ready for generation review."));
        snapshotRepository.save(snapshotEntity);
    }

    @Transactional(readOnly = true)
    public ChatPlanningEvaluationResult readPlanningEvaluation(final UUID chatId, final long currentMessageCount) {
        final Optional<ChatPlanningSnapshotEntity> snapshotEntityOptional = snapshotRepository.findByChatId(chatId);
        if (snapshotEntityOptional.isEmpty()) {
            return new ChatPlanningEvaluationResult(
                    ChatPlanningReadinessStatus.ANALYSIS_FAILED,
                    null,
                    currentMessageCount,
                    "Planning analysis is not available for the latest turn. Retry analysis.");
        }

        final ChatPlanningSnapshotEntity snapshotEntity = snapshotEntityOptional.get();
        final ChatPlanningSnapshot snapshot = preparationSnapshotCodec.readChatPlanningSnapshot(snapshotEntity);
        if (snapshotEntity.getLastEvaluatedMessageCount() != currentMessageCount) {
            return new ChatPlanningEvaluationResult(
                    ChatPlanningReadinessStatus.ANALYSIS_FAILED,
                    snapshot,
                    currentMessageCount,
                    "Planning analysis is stale for the latest turn. Retry analysis.");
        }

        return new ChatPlanningEvaluationResult(
                snapshotEntity.getPlanningStatus(),
                snapshot,
                currentMessageCount,
                defaultReason(snapshotEntity.getPlanningStatusReason(), defaultPlanningReason(snapshotEntity.getPlanningStatus())));
    }

    public ChatPlanningSnapshot refreshChatPlanningSnapshot(final UUID userId, final ChatEntity chat, final long targetMessageCount) {
        final Optional<ChatPlanningSnapshotEntity> existingSnapshotEntity = snapshotRepository.findByChatId(chat.getId());
        final ChatPlanningSnapshotEntity snapshotEntity = existingSnapshotEntity.orElse(null);
        final ChatPlanningSnapshot existingSnapshot = snapshotEntity == null
                ? null
                : preparationSnapshotCodec.readChatPlanningSnapshot(snapshotEntity);
        final long lastSummarizedMessageCount = snapshotEntity == null ? 0L : snapshotEntity.getLastMessageCount();

        if (existingSnapshot != null && lastSummarizedMessageCount == targetMessageCount) {
            return existingSnapshot;
        }

        if (countMessages(chat.getId()) != targetMessageCount) {
            throw new IllegalStateException("Planning analysis became stale before snapshot refresh completed");
        }

        final List<MessageEntity> unsummarizedMessages = loadUnsummarizedMessages(chat.getId(), lastSummarizedMessageCount);
        if (unsummarizedMessages.isEmpty()) {
            if (existingSnapshot != null) {
                return existingSnapshot;
            }
            throw new IllegalArgumentException("Conversation does not contain enough context to prepare generation");
        }

        final long updatedMessageCount = lastSummarizedMessageCount + unsummarizedMessages.size();
        final String sanitizedConversationDelta = PromptInputValidator.validateAndSanitizeChatContext(
                buildPromptSummary(unsummarizedMessages));
        final ChatPlanningSnapshot refreshedSnapshot = refreshChatPlanningSnapshotWithRetry(
                userId,
                Optional.ofNullable(existingSnapshot),
                sanitizedConversationDelta,
                chat.getId());

        if (countMessages(chat.getId()) != targetMessageCount) {
            return refreshedSnapshot;
        }

        if (snapshotEntity != null) {
            snapshotEntity.updateSnapshot(
                    updatedMessageCount,
                    refreshedSnapshot.contractVersion(),
                    preparationSnapshotCodec.toPayload(refreshedSnapshot));
            snapshotRepository.save(snapshotEntity);
            return refreshedSnapshot;
        }

        snapshotRepository.save(ChatPlanningSnapshotEntity.create(
                chat,
                updatedMessageCount,
                refreshedSnapshot.contractVersion(),
                preparationSnapshotCodec.toPayload(refreshedSnapshot)));
        return refreshedSnapshot;
    }

    @Transactional
    public void updatePlanningStatusIfCurrent(
            final UUID chatId,
            final long targetMessageCount,
            final ChatPlanningReadinessStatus status,
            final String reason) {
        snapshotRepository.findByChatId(chatId).ifPresent(snapshotEntity -> {
            if (snapshotEntity.getLastEvaluatedMessageCount() != targetMessageCount) {
                log.debug(
                        "Skipping stale planning status update: chatId={}, expectedMessages={}, actualMessages={}",
                        chatId,
                        targetMessageCount,
                        snapshotEntity.getLastEvaluatedMessageCount());
                return;
            }

            snapshotEntity.updateEvaluation(targetMessageCount, status, reason);
            snapshotRepository.save(snapshotEntity);
        });
    }

    private ChatPlanningSnapshot refreshChatPlanningSnapshotWithRetry(
            final UUID userId,
            final Optional<ChatPlanningSnapshot> previousSnapshot,
            final String sanitizedConversationDelta,
            final UUID chatId) {
        LlmResponseParseException lastException = null;

        for (int attempt = 1; attempt <= MAX_SNAPSHOT_REFRESH_ATTEMPTS; attempt++) {
            try {
                return promptAnalyzerService.refreshChatPlanningSnapshot(userId, previousSnapshot, sanitizedConversationDelta);
            } catch (final LlmResponseParseException exception) {
                lastException = exception;
                if (attempt < MAX_SNAPSHOT_REFRESH_ATTEMPTS) {
                    log.warn(
                            "Retrying chat planning snapshot refresh after parse failure: chatId={}, attempt={}, reason={}",
                            chatId,
                            attempt,
                            exception.getMessage());
                }
            }
        }

        throw lastException == null
                ? new IllegalStateException("Chat planning snapshot refresh failed without a parse exception")
                : lastException;
    }

    public String resolveProjectName(final ChatEntity chat, final StructuredAnalysis analysis) {
        if (chat.getConversationProject() == null) {
            return resolvedAnalysisProjectName(analysis);
        }

        final com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity project = chat.getConversationProject();
        if (project.isAutoNamed()) {
            return resolvedAnalysisProjectName(analysis);
        }

        final String rawProjectName = project.getName();
        if (rawProjectName == null || rawProjectName.isBlank()) {
            return resolvedAnalysisProjectName(analysis);
        }
        return rawProjectName.trim();
    }

    public String buildPreparationPromptSummary(
            final ChatPlanningSnapshot snapshot,
            final List<MessageEntity> recentMessages) {
        return """
                Project name: %s
                Project type: %s
                Scope summary: %s
                Stack summary: %s
                Target users: %s
                Confirmed scope decisions: %s
                Confirmed stack decisions: %s
                Planning handoff: %s
                Planning handoff summary: %s
                Explicit open questions: %s
                Explicit unresolved constraints: %s
                Latest concrete messages:
                %s
                """.formatted(
                snapshot.projectName(),
                snapshot.projectType(),
                snapshot.scopeSummary(),
                snapshot.stackSummary(),
                snapshot.targetUserSummary(),
                String.join(", ", snapshot.confirmedScopeDecisions()),
                String.join(", ", snapshot.confirmedStackDecisions()),
                snapshot.handoffState().name(),
                snapshot.handoffSummary(),
                String.join(", ", snapshot.openQuestions()),
                String.join(", ", snapshot.unresolvedConstraints()),
                buildPromptSummary(recentMessages));
    }

    private List<MessageEntity> loadUnsummarizedMessages(final UUID chatId, final long lastSummarizedMessageCount) {
        if (lastSummarizedMessageCount <= 0) {
            return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId);
        }
        return messageRepository.findUnsummarizedMessagesByChatId(chatId, lastSummarizedMessageCount);
    }

    private String buildPromptSummary(final List<MessageEntity> recentMessages) {
        if (recentMessages.isEmpty()) {
            throw new IllegalArgumentException("Conversation does not contain enough context to prepare generation");
        }

        return recentMessages.stream()
                .map(message -> message.getRole().name() + ": " + message.getContent())
                .reduce((left, right) -> left + "\n" + right)
                .orElseThrow(() -> new IllegalArgumentException("Conversation does not contain enough context to prepare generation"));
    }

    private String resolvedAnalysisProjectName(final StructuredAnalysis analysis) {
        if (analysis != null && analysis.projectName() != null && !analysis.projectName().isBlank()) {
            return analysis.projectName().trim();
        }
        return "Untitled Project";
    }

    private String defaultPlanningReason(final ChatPlanningReadinessStatus status) {
        return switch (status) {
            case ANALYZING -> "Planning analysis is running.";
            case READY_FOR_PREPARATION -> "Planning conversation is ready for generation review.";
            case ANALYSIS_FAILED -> "Planning analysis failed for the latest turn.";
            case DISCOVERY_IN_PROGRESS -> "Planning conversation is still being refined.";
        };
    }

    private String defaultReason(final String value, final String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.trim();
    }
}
