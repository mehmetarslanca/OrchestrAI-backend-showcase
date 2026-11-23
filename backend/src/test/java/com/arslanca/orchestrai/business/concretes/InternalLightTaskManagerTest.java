package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.dataaccess.abstracts.ChatRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ConversationProjectRepository;
import com.arslanca.orchestrai.business.abstracts.AiExecutionRouteResolverService;
import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InternalLightTaskManagerTest {

  @Mock
  private ChatModel lightChatModel;
  @Mock
  private AiExecutionRouteResolverService aiExecutionRouteResolverService;
  @Mock
  private AiUsageCaptureService aiUsageCaptureService;
  @Mock
  private ConversationProjectRepository projectRepository;
  @Mock
  private ChatRepository chatRepository;
  @Mock
  private ObjectProvider<TaskExecutor> namedExecutorProvider;
  @Mock
  private ObjectProvider<TaskExecutor> genericExecutorProvider;

  private InternalLightTaskManager taskManager;

  @BeforeEach
  void setUp() {
    when(namedExecutorProvider.getIfAvailable()).thenReturn(new SyncTaskExecutor());
    lenient().when(aiExecutionRouteResolverService.resolve(any(), any(), any())).thenReturn(
        new ResolvedAiExecutionRoute(AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE, lightChatModel, null, null));
    taskManager = new InternalLightTaskManager(
        aiExecutionRouteResolverService,
        aiUsageCaptureService,
        projectRepository,
        chatRepository,
        namedExecutorProvider,
        genericExecutorProvider);
  }

  @Test
  @DisplayName("Should not generate name if project is not auto-named")
  void shouldNotGenerateIfProjectNotAutoNamed() {
    UUID id = UUID.randomUUID();
    taskManager.suggestProjectNameAsync(id, false, "hello");

    verifyNoInteractions(lightChatModel);
    verifyNoInteractions(projectRepository);
  }

  @Test
  @DisplayName("Should sanitize and truncate generated project name")
  void shouldSanitizeAndTruncateProjectName() {
    UUID id = UUID.randomUUID();

    String longMaliciousName = "New\" Project\nName\r" + "A".repeat(250);

    ChatResponse mockResponse = new ChatResponse(List.of(
        new Generation(new org.springframework.ai.chat.messages.AssistantMessage(longMaliciousName))));
    when(lightChatModel.call(any(Prompt.class))).thenReturn(mockResponse);

    when(projectRepository.updateNameIfAutoNamed(eq(id), anyString(), eq(false))).thenReturn(1);

    taskManager.suggestProjectNameAsync(UUID.randomUUID(), id, true, "hello");

    ArgumentCaptor<String> nameCaptor = ArgumentCaptor.forClass(String.class);
    verify(projectRepository).updateNameIfAutoNamed(eq(id), nameCaptor.capture(), eq(false));
    verify(aiUsageCaptureService).capture(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());

    String savedName = nameCaptor.getValue();
    assertThat(savedName).doesNotContain("\"").doesNotContain("\n").doesNotContain("\r");
    assertThat(savedName.length()).isLessThanOrEqualTo(200);
    assertThat(savedName).startsWith("New ProjectName");
  }

  @Test
  @DisplayName("Should capture failed usage when project rename persistence fails")
  void shouldCaptureFailedUsageWhenProjectRenamePersistenceFails() {
    UUID id = UUID.randomUUID();

    ChatResponse mockResponse = new ChatResponse(List.of(
        new Generation(new org.springframework.ai.chat.messages.AssistantMessage("Project Name"))));
    when(lightChatModel.call(any(Prompt.class))).thenReturn(mockResponse);
    doThrow(new RuntimeException("db write failed"))
        .when(projectRepository)
        .updateNameIfAutoNamed(eq(id), anyString(), eq(false));

    taskManager.suggestProjectNameAsync(UUID.randomUUID(), id, true, "hello");

    verify(aiUsageCaptureService).capture(any(), any(), any(), eq(com.arslanca.orchestrai.entity.enums.AiUsageEventStatus.FAILED),
        any(), any(), any(), any(), any(), any(), any());
  }

}
