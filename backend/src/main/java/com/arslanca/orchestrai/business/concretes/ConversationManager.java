package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.abstracts.ConversationService;
import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.AiExecutionRouteResolverService;
import com.arslanca.orchestrai.business.dtos.conversation.ChatResponse;
import com.arslanca.orchestrai.business.dtos.conversation.ConversationProjectResponse;
import com.arslanca.orchestrai.business.dtos.conversation.CreateChatRequest;
import com.arslanca.orchestrai.business.dtos.conversation.CreateProjectRequest;
import com.arslanca.orchestrai.business.dtos.conversation.MessageResponse;
import com.arslanca.orchestrai.business.dtos.conversation.SendMessageRequest;
import com.arslanca.orchestrai.business.dtos.conversation.ChatSseEvent;
import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatPlanningStatusResponse;
import com.arslanca.orchestrai.business.enums.ChatPlanningReadinessStatus;
import com.arslanca.orchestrai.business.planning.preparation.ChatPlanningHandoffTool;
import com.arslanca.orchestrai.business.planning.preparation.TransientChatPlanningHandoffRegistry;
import com.arslanca.orchestrai.business.prompt.SystemPrompts;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.core.exception.type.ResourceNotFoundException;
import com.arslanca.orchestrai.core.ai.ProviderFailureClassifier;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.core.exception.type.InsufficientCreditException;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ConversationProjectRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity;
import com.arslanca.orchestrai.entity.concretes.MessageEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import com.arslanca.orchestrai.entity.enums.MessageRole;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
public class ConversationManager implements ConversationService {

  private static final String CORRELATION_ID_KEY = "correlationId";

  private final ConversationProjectRepository projectRepository;
  private final ChatRepository chatRepository;
  private final MessageRepository messageRepository;
  private final UserRepository userRepository;
  private final CreditManagementService creditManagementService;
  private final ChatMessageTransactionService chatMessageTransactionService;
  private final DistributedChatStreamRegistry chatStreamRegistry;
  private final com.arslanca.orchestrai.business.abstracts.InternalLightTaskService internalLightTaskService;
  private final ProjectGenerationPreparationService generationPreparationService;
  private final TransientChatPlanningHandoffRegistry planningHandoffRegistry;
  private final AiExecutionRouteResolverService aiExecutionRouteResolverService;
  private final AiUsageCaptureService aiUsageCaptureService;
  private final AiProviderRuntimeProperties aiProviderRuntimeProperties;

  private final String modelName;

  public ConversationManager(ConversationProjectRepository projectRepository, ChatRepository chatRepository,
      MessageRepository messageRepository, UserRepository userRepository,
      CreditManagementService creditManagementService, ChatMessageTransactionService chatMessageTransactionService,
      DistributedChatStreamRegistry chatStreamRegistry,
      AiExecutionRouteResolverService aiExecutionRouteResolverService,
      AiUsageCaptureService aiUsageCaptureService,
      AiProviderRuntimeProperties aiProviderRuntimeProperties,
      ProjectGenerationPreparationService generationPreparationService,
      TransientChatPlanningHandoffRegistry planningHandoffRegistry,
      com.arslanca.orchestrai.business.abstracts.InternalLightTaskService internalLightTaskService,
      @org.springframework.beans.factory.annotation.Value("${orchestrai.ai.light-model:light-model}") String modelName) {
    this.projectRepository = projectRepository;
    this.chatRepository = chatRepository;
    this.messageRepository = messageRepository;
    this.userRepository = userRepository;
    this.creditManagementService = creditManagementService;
    this.chatMessageTransactionService = chatMessageTransactionService;
    this.chatStreamRegistry = chatStreamRegistry;
    this.aiExecutionRouteResolverService = aiExecutionRouteResolverService;
    this.aiUsageCaptureService = aiUsageCaptureService;
    this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
    this.generationPreparationService = generationPreparationService;
    this.planningHandoffRegistry = planningHandoffRegistry;
    this.internalLightTaskService = internalLightTaskService;
    this.modelName = modelName;
  }

  private static class StreamContext {
    final Disposable subscription;
    final StringBuilder responseBuilder;
    final java.util.concurrent.atomic.AtomicBoolean hasSentTokens;
    final Prompt prompt;
    final ResolvedAiExecutionRoute executionRoute;
    final OffsetDateTime startedAt;
    final String requestId;
    final AtomicReference<org.springframework.ai.chat.model.ChatResponse> lastResponse;

    StreamContext(
        Disposable subscription,
        StringBuilder responseBuilder,
        java.util.concurrent.atomic.AtomicBoolean hasSentTokens,
        Prompt prompt,
        ResolvedAiExecutionRoute executionRoute,
        OffsetDateTime startedAt,
        String requestId,
        AtomicReference<org.springframework.ai.chat.model.ChatResponse> lastResponse) {
      this.subscription = subscription;
      this.responseBuilder = responseBuilder;
      this.hasSentTokens = hasSentTokens;
      this.prompt = prompt;
      this.executionRoute = executionRoute;
      this.startedAt = startedAt;
      this.requestId = requestId;
      this.lastResponse = lastResponse;
    }
  }

  private record ConversationProjectSnapshot(UUID projectId, boolean isAutoNamed, String projectName) {
  }

  // Chat ID -> Active Stream Context (for stopStream functionality)
  private final ConcurrentHashMap<UUID, StreamContext> activeStreams = new ConcurrentHashMap<>();

  // Placeholder to prevent race conditions during stream initialization
  private static final StreamContext PLACEHOLDER_CONTEXT = new StreamContext(() -> {
  }, new StringBuilder(), new java.util.concurrent.atomic.AtomicBoolean(false), null, null, null, null,
      new AtomicReference<>());

  // Maximum number of messages to send to LLM as context
  private static final int MAX_CONTEXT_MESSAGES = 20;

  @Override
  @Transactional(readOnly = true)
  public List<ConversationProjectResponse> getUserProjects(UUID userId) {
    log.debug("Fetching conversation projects for user: {}", userId);
    return projectRepository.findByUserIdOrderByUpdatedAtDesc(userId)
        .stream()
        .map(this::mapToProjectResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public ConversationProjectResponse createProject(UUID userId, CreateProjectRequest request) {
    log.info("Creating new conversation project for user: {}, name: {}", userId, request.name());

    UserEntity user = userRepository.findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

    boolean isAutoNamed = request.name() == null || request.name().isBlank();
    String name = isAutoNamed ? "Untitled Project" : request.name().trim();

    ConversationProjectEntity project = ConversationProjectEntity.builder()
        .user(user)
        .name(name)
        .isAutoNamed(isAutoNamed)
        .build();

    ConversationProjectEntity saved = projectRepository.save(project);
    return mapToProjectResponse(saved);
  }

  @Override
  @Transactional
  public void deleteProject(UUID userId, UUID projectId) {
    log.info("Deleting conversation project: {}, by user: {}", projectId, userId);

    ConversationProjectEntity project = projectRepository.findByIdAndUserId(projectId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Conversation project not found or access denied"));

    // Stop all active streams for chats in this project
    if (project.getChats() != null) {
      project.getChats().forEach(chat -> stopStream(userId, chat.getId()));
    }

    projectRepository.delete(project);
  }

  @Override
  @Transactional(readOnly = true)
  public List<ChatResponse> getProjectChats(UUID userId, UUID projectId) {
    log.debug("Fetching chats for project: {}", projectId);

    // Verify ownership
    projectRepository.findByIdAndUserId(projectId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Conversation project not found or access denied"));

    return chatRepository.findByConversationProjectIdOrderByCreatedAtDesc(projectId)
        .stream()
        .map(this::mapToChatResponse)
        .collect(Collectors.toList());
  }

  @Override
  @Transactional
  public ChatResponse createChat(UUID userId, UUID projectId, CreateChatRequest request) {
    log.info("Creating new chat for project: {}", projectId);

    ConversationProjectEntity project = projectRepository.findByIdAndUserId(projectId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Conversation project not found or access denied"));

    ChatEntity chat = ChatEntity.builder()
        .conversationProject(project)
        .title("New Chat")
        .isAutoNamed(true)
        .build();

    ChatEntity saved = chatRepository.save(chat);
    return mapToChatResponse(saved);
  }

  @Override
  @Transactional
  public void deleteChat(UUID userId, UUID chatId) {
    log.info("Deleting chat: {}, by user: {}", chatId, userId);

    ChatEntity chat = chatRepository.findByIdAndUserId(chatId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Chat not found or access denied"));

    // If there's an active stream, stop it before deleting
    stopStream(userId, chatId);

    chatRepository.delete(chat);
  }

  @Override
  @Transactional(readOnly = true)
  public List<MessageResponse> getChatMessages(UUID userId, UUID chatId) {
    log.debug("Fetching messages for chat: {}", chatId);

    // Verify ownership
    chatRepository.findByIdAndUserId(chatId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Chat not found or access denied"));

    return messageRepository.findByChatIdOrderByCreatedAtAsc(chatId)
        .stream()
        .map(this::mapToMessageResponse)
        .collect(Collectors.toList());
  }

  @Override
  public SseEmitter sendMessageAndStream(UUID userId, UUID chatId, SendMessageRequest request) {
    log.info("Sending message to chat: {}", chatId);

    // 1. Check credit balance early
    if (creditManagementService.getCreditBalance(userId).balance() < 1) {
      throw new InsufficientCreditException("Yetersiz kredi: userId=" + userId);
    }

    // 2. Lock chat statelessly via DB and save user message
    ChatEntity chat;
    try {
      chat = chatMessageTransactionService.lockChatForGeneration(chatId, userId, request.content());
    } catch (Exception e) {
      throw e;
    }

    try {
      final ConversationProjectSnapshot projectSnapshot = snapshotConversationProject(chat);

      // Fetch context messages (last N messages)
      List<MessageEntity> recentMessages = messageRepository.findRecentMessagesByChatId(chatId, MAX_CONTEXT_MESSAGES);

      // Re-order messages to be chronological
      java.util.Collections.reverse(recentMessages);

      // Build Prompt with token limit logic
      List<org.springframework.ai.chat.messages.Message> springAiMessages = new java.util.ArrayList<>();
      springAiMessages.add(new SystemMessage(SystemPrompts.CHAT_SYSTEM_PROMPT));

      long messageCount = messageRepository.countByChatId(chatId);
      final boolean shouldScheduleNaming = messageCount == 1;
      final boolean shouldDeferNamingUntilSuccess = shouldScheduleNaming && shouldDeferAutoNamingUntilSuccess();
      if (shouldScheduleNaming && !shouldDeferNamingUntilSuccess) {
        // Trigger async naming utilities safely
        internalLightTaskService.suggestChatTitleAsync(userId, chat, request.content());

        internalLightTaskService.suggestProjectNameAsync(
            userId,
            projectSnapshot.projectId(),
            projectSnapshot.isAutoNamed(),
            request.content());
      }

      // Limit total context characters to ~8000
      int currentLength = 0;
      int MAX_CHARS = 8000;

      // We iterate from newest to oldest to keep the most recent context if it
      // exceeds limit
      // recentMessages is chronological, so we loop backwards to count length
      List<MessageEntity> allowedMessages = new java.util.ArrayList<>();
      for (int i = recentMessages.size() - 1; i >= 0; i--) {
        MessageEntity msg = recentMessages.get(i);
        int msgLen = msg.getContent().length();
        if (currentLength + msgLen <= MAX_CHARS || allowedMessages.isEmpty()) {
          // we always allow at least the newest message even if it's large (though
          // incoming limit is 8k)
          allowedMessages.add(0, msg);
          currentLength += msgLen;
        } else {
          // Exceeded limit, skip older messages
          break;
        }
      }

      for (MessageEntity msg : allowedMessages) {
        if (msg.getRole() == MessageRole.USER) {
          springAiMessages.add(new UserMessage(msg.getContent()));
        } else if (msg.getRole() == MessageRole.ASSISTANT) {
          springAiMessages.add(new AssistantMessage(msg.getContent()));
        }
      }

      Prompt prompt = new Prompt(
          springAiMessages,
          ToolCallingChatOptions.builder()
              .toolCallbacks(ToolCallbacks.from(new ChatPlanningHandoffTool(chatId, planningHandoffRegistry)))
              .internalToolExecutionEnabled(true)
              .build());

      final ResolvedAiExecutionRoute executionRoute = aiExecutionRouteResolverService.resolve(userId, null, null);
      final OffsetDateTime startedAt = OffsetDateTime.now();
      final String requestId = currentCorrelationId();
      log.info(
          "Chat execution route resolved: chatId={}, userId={}, executionClass={}, model={}",
          chatId,
          userId,
          executionRoute.executionClass(),
          routeLabel(executionRoute));

      // Initialize SseEmitter (timeout 5 minutes)
      SseEmitter emitter = new SseEmitter(300_000L);

      // Call LLM Stream
      Flux<org.springframework.ai.chat.model.ChatResponse> stream = executionRoute.chatModel().stream(prompt);

      StringBuilder fullResponse = new StringBuilder();
      java.util.concurrent.atomic.AtomicBoolean hasSentTokens = new java.util.concurrent.atomic.AtomicBoolean(false);
      AtomicReference<org.springframework.ai.chat.model.ChatResponse> lastResponse = new AtomicReference<>();

      // Fix lifecycle ordering: Register context before subscribe so synchronous
      // completions don't miss the cleanup
      StreamContext initialContext = new StreamContext(() -> {
      }, fullResponse, hasSentTokens, prompt, executionRoute, startedAt, requestId, lastResponse);
      activeStreams.put(chatId, initialContext);
      chatStreamRegistry.registerStream(chatId, userId);

      Disposable subscription = stream.subscribe(
          chatResponse -> {
            lastResponse.set(chatResponse);
            String content = chatResponse.getResult().getOutput().getText();
            if (content != null && !content.isEmpty()) {
              hasSentTokens.set(true);
              fullResponse.append(content);
              try {
                emitter.send(SseEmitter.event()
                    .name("TOKEN")
                    .data(ChatSseEvent.token(content)));
              } catch (IOException e) {
                log.warn("Failed to send token event to client for chat {}: {}", chatId, e.getMessage());
                emitter.complete();
                internalStopStream(userId, chatId, false);
              }
            }
          },
          error -> {
            ProviderFailureClassifier.FailureType failureType = ProviderFailureClassifier.classify(error);

                log.error(
                    "Provider failure | provider: spring-ai | model: {} | failureType: {} | refundApplied: true | chatId: {} | userId: {}",
                    routeLabel(executionRoute), failureType, chatId, userId);

            String userMsg = ProviderFailureClassifier.getUserFriendlyMessage(failureType);

            try {
              emitter.send(SseEmitter.event()
                  .name("ERROR")
                  .data(ChatSseEvent.error(userMsg)));
            } catch (IOException e) {
              log.warn("Failed to send ERROR event to client for chat {}: {}", chatId, e.getMessage());
            }

            // Fix order to prevent onCompletion from stealing the state and bypassing
            // refund
            internalStopStream(
                userId,
                chatId,
                true,
                AiUsageEventStatus.FAILED,
                Map.of(
                    "failureType", failureType.name(),
                    "failureMessage", userMsg));
            // Phase 10.2.1: Graceful complete to avoid MVC error handler running again on
            // committed stream
            emitter.complete();
          },
          () -> {
            // Stream completed successfully
            log.info("Stream completed for chat: {}", chatId);
            final String assistantResponse = fullResponse.toString();
            try {
              // Save assistant message to DB (credit is already deducted)
              chatMessageTransactionService.saveAssistantMessageAndUnlock(chatId, userId, assistantResponse);
              if (shouldDeferNamingUntilSuccess) {
                triggerAutoNaming(userId, chat, projectSnapshot, request.content());
              }
              captureChatUsage(
                  userId,
                  executionRoute,
                  prompt,
                  assistantResponse,
                  lastResponse.get(),
                  startedAt,
                  requestId,
                  AiUsageEventStatus.COMPLETED,
                  Map.of(
                      "chatId", chatId.toString(),
                      "projectId", projectSnapshot.projectId().toString(),
                      "ideTarget", request.ideTarget() == null ? "default" : request.ideTarget()));
            } catch (Exception e) {
              captureChatUsage(
                  userId,
                  executionRoute,
                  prompt,
                  assistantResponse,
                  lastResponse.get(),
                  startedAt,
                  requestId,
                  AiUsageEventStatus.FAILED,
                  Map.of(
                      "chatId", chatId.toString(),
                      "projectId", projectSnapshot.projectId().toString(),
                      "ideTarget", request.ideTarget() == null ? "default" : request.ideTarget(),
                      "failureReason", e.getClass().getSimpleName(),
                      "failureMessage", e.getMessage() == null ? "unknown" : e.getMessage()));
              log.error("Error saving stream for chat {}: {}", chatId, e.getMessage());
              chatMessageTransactionService.unlockChatAndRefund(chatId, userId, !hasSentTokens.get());
              emitter.complete();
              activeStreams.remove(chatId);
              chatStreamRegistry.clearStream(chatId);
              return;
            }

            registerPendingPlanningHandoff(userId, chatId);
            final ChatPlanningStatusResponse planningStatus = resolvePlanningStatusAfterAssistantTurn(
                userId,
                chatId,
                request.ideTarget());

            activeStreams.remove(chatId); // Remove before completing emitter to avoid cancel unlock
            chatStreamRegistry.clearStream(chatId);

            try {
              emitter.send(SseEmitter.event()
                  .name("DONE")
                  .data(ChatSseEvent.done(planningStatus)));
              emitter.complete();
            } catch (Exception e) {
              log.warn("Error sending DONE event to client for chat {}: {}", chatId, e.getMessage());
              emitter.complete();
            }
          });

      // Update the subscription in the context, taking care not to overwrite if it
      // was already removed
      activeStreams.computeIfPresent(chatId,
          (id, oldContext) -> new StreamContext(subscription, fullResponse, hasSentTokens, prompt, executionRoute,
              startedAt, requestId, lastResponse));

      // Handle client disconnect properly to avoid leak
      emitter.onCompletion(() -> {
        internalStopStream(userId, chatId);
      });
      emitter.onTimeout(() -> {
        log.warn("SSE Emitter timeout for chat: {}", chatId);
        internalStopStream(
            userId,
            chatId,
            true,
            AiUsageEventStatus.CANCELLED,
            Map.of("failureReason", "sse-timeout"));
      });
      emitter.onError(e -> {
        log.warn("SSE Emitter error for chat: {}: {}", chatId, e.getMessage());
        internalStopStream(userId, chatId);
      });

      return emitter;

    } catch (Exception e) {
      planningHandoffRegistry.clear(chatId);
      captureFailedChatUsage(userId, chatId, e);
      chatMessageTransactionService.unlockChatAndRefund(chatId, userId, true);
      activeStreams.remove(chatId);
      chatStreamRegistry.clearStream(chatId);
      throw e;
    }
  }

  @Override
  public void stopStream(UUID userId, UUID chatId) {
    log.info("Stopping stream for chat: {}", chatId);

    // Verify ownership (lightweight)
    chatRepository.findByIdAndUserId(chatId, userId)
        .orElseThrow(() -> new ResourceNotFoundException("Chat not found or access denied"));

    if (!chatStreamRegistry.hasActiveStream(chatId) && !activeStreams.containsKey(chatId)) {
      return;
    }

    if (activeStreams.containsKey(chatId)) {
      internalStopStream(userId, chatId);
      return;
    }

    chatStreamRegistry.requestCancel(chatId, userId);
  }

  public void internalStopStreamWithoutUserId(UUID chatId) {
    final UUID ownerUserId = chatStreamRegistry.getOwnerUserId(chatId);
    if (ownerUserId != null) {
      internalStopStream(ownerUserId, chatId);
      return;
    }

    chatRepository.findOwnerUserIdByChatId(chatId)
        .ifPresent(resolvedOwnerUserId -> internalStopStream(resolvedOwnerUserId, chatId));
  }

  private void internalStopStream(UUID userId, UUID chatId) {
    internalStopStream(userId, chatId, false, AiUsageEventStatus.CANCELLED,
        Map.of("failureReason", "stream-cancelled"));
  }

  private void internalStopStream(UUID userId, UUID chatId, boolean forceRefund) {
    internalStopStream(userId, chatId, forceRefund, AiUsageEventStatus.CANCELLED,
        Map.of("failureReason", forceRefund ? "forced-refund" : "stream-cancelled"));
  }

  private void internalStopStream(
      UUID userId,
      UUID chatId,
      boolean forceRefund,
      AiUsageEventStatus usageStatus,
      Map<String, Object> usageMetadata) {
    StreamContext context = activeStreams.remove(chatId);
    chatStreamRegistry.clearStream(chatId);
    planningHandoffRegistry.clear(chatId);
    // Process if it's an actual active stream
    if (context != null && context != PLACEHOLDER_CONTEXT) {
      if (!context.subscription.isDisposed()) {
        context.subscription.dispose();
      }
      log.info("Stream stopped for chat: {}, forceRefund: {}", chatId, forceRefund);

      String partialResponse = context.responseBuilder.toString();
      try {
        if (forceRefund) {
          // Provider failure or specific network timeout that forces refund
          log.info("Applying forced refund for chat: {}", chatId);
          chatMessageTransactionService.unlockChatAndRefund(chatId, userId, true);
        } else {
          // User stopped
          if (!partialResponse.isEmpty()) {
            // Cancelled but already generated tokens: save what was generated, NO refund.
            chatMessageTransactionService.saveAssistantMessageAndUnlock(chatId, userId, partialResponse);
          } else {
            // Cancelled before tokens sent, or network timeout: refund the credit.
            chatMessageTransactionService.unlockChatAndRefund(chatId, userId, true);
          }
        }
      } catch (Exception e) {
        log.error("Failed to unlock chat during stream stop: {}", e.getMessage());
      } finally {
        captureChatUsage(
            userId,
            context.executionRoute,
            context.prompt,
            partialResponse,
            context.lastResponse.get(),
            context.startedAt,
            context.requestId,
            usageStatus,
            mergeChatUsageMetadata(chatId, usageMetadata));
      }
    }
  }

  private ChatPlanningStatusResponse resolvePlanningStatusAfterAssistantTurn(
      final UUID userId,
      final UUID chatId,
      final String ideTarget) {
    try {
      return generationPreparationService.evaluateChatPlanningAfterAssistantTurn(userId, chatId, ideTarget);
    } catch (Exception exception) {
      log.warn(
          "Chat planning handoff evaluation failed after assistant turn: chatId={}, userId={}, reason={}",
          chatId,
          userId,
          exception.getMessage());
      return ChatPlanningStatusResponse.notReady(
          ChatPlanningReadinessStatus.ANALYSIS_FAILED,
          "The latest planning handoff could not be evaluated automatically.",
          null);
    }
  }

  private void registerPendingPlanningHandoff(final UUID userId, final UUID chatId) {
    final java.util.Optional<ChatPlanningSnapshot> pendingHandoff = planningHandoffRegistry.consume(chatId);
    if (pendingHandoff.isEmpty()) {
      return;
    }

    try {
      generationPreparationService.registerToolDrivenPlanningHandoff(userId, chatId, pendingHandoff.get());
      log.info("Registered tool-driven planning handoff for chat: {}", chatId);
    } catch (Exception exception) {
      log.warn(
          "Failed to register tool-driven planning handoff after assistant turn: chatId={}, userId={}, reason={}",
          chatId,
          userId,
          exception.getMessage());
    }
  }
  // ---- Helper Mappers ----

  private ConversationProjectResponse mapToProjectResponse(ConversationProjectEntity entity) {
    int chatCount = entity.getChats() != null ? entity.getChats().size() : 0;
    return new ConversationProjectResponse(
        entity.getId(),
        entity.getName(),
        chatCount,
        entity.getCreatedAt(),
        entity.getUpdatedAt());
  }

  private ChatResponse mapToChatResponse(ChatEntity entity) {
    int messageCount = entity.getMessages() != null ? entity.getMessages().size() : 0;
    return new ChatResponse(
        entity.getId(),
        entity.getTitle(),
        messageCount,
        entity.getCreatedAt());
  }

  private MessageResponse mapToMessageResponse(MessageEntity entity) {
    return new MessageResponse(
        entity.getId(),
        entity.getRole().name(),
        entity.getContent(),
        entity.getCreatedAt());
  }

  // ---- Conversation project snapshot ----

  private ConversationProjectSnapshot snapshotConversationProject(final ChatEntity chat) {
    final ConversationProjectEntity conversationProject = chat.getConversationProject();
    if (conversationProject == null) {
      throw new IllegalStateException("Chat is not linked to a conversation project: " + chat.getId());
    }

    final String projectName = conversationProject.getName() != null && !conversationProject.getName().isBlank()
        ? conversationProject.getName()
        : "Untitled Project";

    return new ConversationProjectSnapshot(
        conversationProject.getId(),
        conversationProject.isAutoNamed(),
        projectName);
  }

  private boolean shouldDeferAutoNamingUntilSuccess() {
    return aiProviderRuntimeProperties.testModeEnabled()
        && aiProviderRuntimeProperties.isLocalBaseUrl("openai-compatible");
  }

  private void triggerAutoNaming(
      final UUID userId,
      final ChatEntity chat,
      final ConversationProjectSnapshot projectSnapshot,
      final String firstUserMessage) {
    internalLightTaskService.suggestChatTitleAsync(userId, chat, firstUserMessage);
    internalLightTaskService.suggestProjectNameAsync(
        userId,
        projectSnapshot.projectId(),
        projectSnapshot.isAutoNamed(),
        firstUserMessage);
  }

  private String routeLabel(final ResolvedAiExecutionRoute route) {
    if (route == null || route.runtimeDescriptor() == null) {
      return modelName == null || modelName.isBlank() ? "platform-light" : modelName;
    }
    return route.runtimeDescriptor().providerKey() + ":" + route.runtimeDescriptor().modelKey();
  }

  private void captureChatUsage(
      final UUID userId,
      final ResolvedAiExecutionRoute route,
      final Prompt prompt,
      final String responseText,
      final org.springframework.ai.chat.model.ChatResponse response,
      final OffsetDateTime startedAt,
      final String requestId,
      final AiUsageEventStatus status,
      final Map<String, Object> metadata) {
    if (route == null || prompt == null || startedAt == null) {
      return;
    }
    try {
      aiUsageCaptureService.capture(
          userId,
          requestId,
          AiUsageSurface.CHAT,
          status,
          route,
          prompt,
          responseText,
          response,
          startedAt,
          OffsetDateTime.now(),
          metadata);
    } catch (RuntimeException exception) {
      log.warn("Failed to capture chat AI usage: chatMetadata={}, reason={}", metadata, exception.getMessage());
    }
  }

  private void captureFailedChatUsage(final UUID userId, final UUID chatId, final Exception exception) {
    final StreamContext context = activeStreams.get(chatId);
    if (context == null || context == PLACEHOLDER_CONTEXT) {
      return;
    }
    captureChatUsage(
        userId,
        context.executionRoute,
        context.prompt,
        context.responseBuilder.toString(),
        context.lastResponse.get(),
        context.startedAt,
        context.requestId,
        AiUsageEventStatus.FAILED,
        mergeChatUsageMetadata(chatId, Map.of(
            "failureReason", exception.getClass().getSimpleName(),
            "failureMessage", exception.getMessage() == null ? "unknown" : exception.getMessage())));
  }

  private Map<String, Object> mergeChatUsageMetadata(final UUID chatId, final Map<String, Object> metadata) {
    final java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>();
    merged.put("chatId", chatId.toString());
    if (metadata != null && !metadata.isEmpty()) {
      merged.putAll(metadata);
    }
    return Map.copyOf(merged);
  }

  private String currentCorrelationId() {
    return MDC.get(CORRELATION_ID_KEY);
  }
}
