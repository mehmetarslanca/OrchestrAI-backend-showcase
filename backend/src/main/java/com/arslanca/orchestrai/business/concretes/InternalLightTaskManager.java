package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.InternalLightTaskService;
import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.abstracts.AiExecutionRouteResolverService;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.business.prompt.SystemPrompts;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ConversationProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import com.arslanca.orchestrai.entity.enums.AiUsageEventStatus;
import com.arslanca.orchestrai.entity.enums.AiUsageSurface;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.TaskExecutor;
import org.springframework.stereotype.Service;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;

@Slf4j
@Service
public class InternalLightTaskManager implements InternalLightTaskService {

  private static final String CORRELATION_ID_KEY = "correlationId";

  private final AiExecutionRouteResolverService aiExecutionRouteResolverService;
  private final AiUsageCaptureService aiUsageCaptureService;
  private final ConversationProjectRepository projectRepository;
  private final ChatRepository chatRepository;
  private final TaskExecutor taskExecutor;

  public InternalLightTaskManager(
      final AiExecutionRouteResolverService aiExecutionRouteResolverService,
      final AiUsageCaptureService aiUsageCaptureService,
      ConversationProjectRepository projectRepository,
      ChatRepository chatRepository,
      @Qualifier("applicationTaskExecutor") ObjectProvider<TaskExecutor> applicationTaskExecutorProvider,
      ObjectProvider<TaskExecutor> genericTaskExecutorProvider) {
    this.aiExecutionRouteResolverService = aiExecutionRouteResolverService;
    this.aiUsageCaptureService = aiUsageCaptureService;
    this.projectRepository = projectRepository;
    this.chatRepository = chatRepository;

    // Prefer the named task executor if available, otherwise fall back to any
    // available/primary generic executor
    TaskExecutor executor = applicationTaskExecutorProvider.getIfAvailable();
    if (executor == null) {
      executor = genericTaskExecutorProvider.getIfAvailable();
    }
    if (executor == null) {
      throw new IllegalStateException("No TaskExecutor bean available in application context.");
    }
    this.taskExecutor = executor;
  }

  @Override
  public void suggestProjectNameAsync(UUID userId, UUID projectId, boolean isAutoNamed, String firstUserMessage) {
    if (!isAutoNamed) {
      return;
    }

    final String requestId = MDC.get(CORRELATION_ID_KEY);
    taskExecutor.execute(() -> {
      ResolvedAiExecutionRoute route = null;
      Prompt prompt = null;
      OffsetDateTime startedAt = null;
      org.springframework.ai.chat.model.ChatResponse response = null;
      String responseText = null;
      try {
        route = resolveRoute(userId, "project name suggestion", projectId);
        log.info("Generating project name for project id: {} using route={}", projectId, routeLabel(route));
        SystemMessage systemMessage = new SystemMessage(SystemPrompts.PROJECT_NAMING_SYSTEM_PROMPT);
        UserMessage userMessage = new UserMessage(firstUserMessage);
        prompt = new Prompt(List.of(systemMessage, userMessage));
        startedAt = OffsetDateTime.now();

        response = route.chatModel().call(prompt);
        responseText = response.getResult().getOutput().getText();
        String suggestedName = responseText.trim();

        // Sanitize and truncate
        String finalName = sanitizeName(suggestedName);
        if (finalName.length() > 200) {
          finalName = finalName.substring(0, 200);
        }

        final String safeName = finalName;

        int updated = projectRepository.updateNameIfAutoNamed(projectId, safeName, false);
        if (updated > 0) {
          log.info("Project renamed to: {}", safeName);
        }
        captureUsage(userId, requestId, route, prompt, responseText, response,
            startedAt, "project-name-suggestion", projectId);

      } catch (Exception e) {
        captureFailureUsage(userId, requestId, projectId, firstUserMessage, e, "project-name-suggestion",
            route, prompt, responseText, response, startedAt);
        log.warn("Failed to generate project name: {}", e.getMessage());
      }
    });
  }

  @Override
  public void suggestChatTitleAsync(UUID userId, ChatEntity chat, String firstUserMessage) {
    if (!chat.isAutoNamed()) {
      return;
    }

    final String requestId = MDC.get(CORRELATION_ID_KEY);
    taskExecutor.execute(() -> {
      ResolvedAiExecutionRoute route = null;
      Prompt prompt = null;
      OffsetDateTime startedAt = null;
      org.springframework.ai.chat.model.ChatResponse response = null;
      String responseText = null;
      try {
        route = resolveRoute(userId, "chat title suggestion", chat.getId());
        log.info("Generating chat title for chat id: {} using route={}", chat.getId(), routeLabel(route));
        SystemMessage systemMessage = new SystemMessage(SystemPrompts.CHAT_NAMING_SYSTEM_PROMPT);
        UserMessage userMessage = new UserMessage(firstUserMessage);
        prompt = new Prompt(List.of(systemMessage, userMessage));
        startedAt = OffsetDateTime.now();

        response = route.chatModel().call(prompt);
        responseText = response.getResult().getOutput().getText();
        String suggestedTitle = responseText.trim();

        // Sanitize and truncate
        String finalTitle = sanitizeName(suggestedTitle);
        if (finalTitle.length() > 200) {
          finalTitle = finalTitle.substring(0, 200);
        }

        final String safeTitle = finalTitle;

        int updated = chatRepository.updateTitleIfAutoNamed(chat.getId(), safeTitle, false);
        if (updated > 0) {
          log.info("Chat title updated to: {}", safeTitle);
        }
        captureUsage(userId, requestId, route, prompt, responseText, response,
            startedAt, "chat-title-suggestion", chat.getId());
      } catch (Exception e) {
        captureFailureUsage(userId, requestId, chat.getId(), firstUserMessage, e, "chat-title-suggestion",
            route, prompt, responseText, response, startedAt);
        log.warn("Failed to generate chat title: {}", e.getMessage());
      }
    });
  }

  private ResolvedAiExecutionRoute resolveRoute(final UUID userId, final String operationName, final UUID resourceId) {
    final ResolvedAiExecutionRoute route = aiExecutionRouteResolverService.resolve(userId, null, null);
    log.info("Internal light task route resolved: operation={}, resourceId={}, executionClass={}, model={}",
        operationName,
        resourceId,
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

  private void captureUsage(
      final UUID userId,
      final String requestId,
      final ResolvedAiExecutionRoute route,
      final Prompt prompt,
      final String responseText,
      final org.springframework.ai.chat.model.ChatResponse response,
      final OffsetDateTime startedAt,
      final String operation,
      final UUID resourceId) {
    try {
      aiUsageCaptureService.capture(
          userId,
          requestId,
          AiUsageSurface.INTERNAL_LIGHT_TASK,
          AiUsageEventStatus.COMPLETED,
          route,
          prompt,
          responseText,
          response,
          startedAt,
          OffsetDateTime.now(),
          Map.of(
              "operation", operation,
              "resourceId", resourceId.toString()));
    } catch (RuntimeException captureException) {
      log.warn("Failed to capture internal light task usage: operation={}, resourceId={}, reason={}",
          operation,
          resourceId,
          captureException.getMessage());
    }
  }

  private void captureFailureUsage(
      final UUID userId,
      final String requestId,
      final UUID resourceId,
      final String firstUserMessage,
      final Exception exception,
      final String operation,
      final ResolvedAiExecutionRoute route,
      final Prompt prompt,
      final String responseText,
      final org.springframework.ai.chat.model.ChatResponse response,
      final OffsetDateTime startedAt) {
    try {
      final ResolvedAiExecutionRoute effectiveRoute = route == null ? resolveRoute(userId, operation, resourceId) : route;
      final Prompt effectivePrompt = prompt == null ? new Prompt(List.of(
          new SystemMessage(operation.startsWith("project")
              ? SystemPrompts.PROJECT_NAMING_SYSTEM_PROMPT
              : SystemPrompts.CHAT_NAMING_SYSTEM_PROMPT),
          new UserMessage(firstUserMessage))) : prompt;
      aiUsageCaptureService.capture(
          userId,
          requestId,
          AiUsageSurface.INTERNAL_LIGHT_TASK,
          AiUsageEventStatus.FAILED,
          effectiveRoute,
          effectivePrompt,
          responseText,
          response,
          startedAt == null ? OffsetDateTime.now() : startedAt,
          OffsetDateTime.now(),
          Map.of(
              "operation", operation,
              "resourceId", resourceId.toString(),
              "failureReason", exception.getClass().getSimpleName(),
              "failureMessage", exception.getMessage() == null ? "unknown" : exception.getMessage()));
    } catch (RuntimeException captureException) {
      log.debug("Failed to capture internal light task failure usage: {}", captureException.getMessage());
    }
  }

  private String sanitizeName(String name) {
    return name.replaceAll("[^a-zA-Z0-9 _.,?!-]", "").trim();
  }
}
