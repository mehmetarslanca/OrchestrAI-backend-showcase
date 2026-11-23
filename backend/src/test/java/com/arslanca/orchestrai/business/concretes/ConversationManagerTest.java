package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.AiExecutionRouteResolverService;
import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.planning.preparation.TransientChatPlanningHandoffRegistry;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.business.dtos.conversation.SendMessageRequest;
import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.core.exception.type.DuplicateRequestException;
import com.arslanca.orchestrai.core.exception.type.InsufficientCreditException;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ConversationProjectRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import com.arslanca.orchestrai.entity.concretes.MessageEntity;
import com.arslanca.orchestrai.entity.enums.MessageRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ConversationManagerTest {

  @Mock
  private ConversationProjectRepository projectRepository;
  @Mock
  private ChatRepository chatRepository;
  @Mock
  private MessageRepository messageRepository;
  @Mock
  private UserRepository userRepository;
  @Mock
  private CreditManagementService creditManagementService;
  @Mock
  private ChatMessageTransactionService chatMessageTransactionService;
  @Mock
  private ChatModel chatModel;
  @Mock
  private AiProviderRuntimeProperties aiProviderRuntimeProperties;
  @Mock
  private DistributedChatStreamRegistry chatStreamRegistry;
  @Mock
  private com.arslanca.orchestrai.business.abstracts.InternalLightTaskService internalLightTaskService;
  @Mock
  private AiExecutionRouteResolverService aiExecutionRouteResolverService;
  @Mock
  private AiUsageCaptureService aiUsageCaptureService;
  @Mock
  private ProjectGenerationPreparationService generationPreparationService;
  @Mock
  private TransientChatPlanningHandoffRegistry planningHandoffRegistry;
  @InjectMocks
  private ConversationManager conversationManager;

  private UUID userId;
  private UUID chatId;
  private ChatEntity chat;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    chatId = UUID.randomUUID();

    com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity project =
        com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity.builder()
            .id(UUID.randomUUID())
            .name("Untitled Project")
            .isAutoNamed(false)
            .build();

    chat = ChatEntity.builder()
        .id(chatId)
        .conversationProject(project)
        .build();

    lenient().when(aiExecutionRouteResolverService.resolve(any(), any(), any())).thenReturn(
        new ResolvedAiExecutionRoute(
            AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE,
            chatModel,
            null,
            null));
  }

  @Nested
  @DisplayName("Streaming & Concurrency Tests")
  class StreamingTests {

    @Test
    @DisplayName("Should prevent concurrent message sending to the same chat (TOCTOU protection)")
    void shouldPreventConcurrentMessageSending() {
      SendMessageRequest request = new SendMessageRequest("Hello AI", null);
      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(5, 0));
      when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "Hello AI"))
          .thenThrow(new DuplicateRequestException(
              "There is already an active AI generation for this chat. Please wait or stop it."));

      assertThatThrownBy(() -> conversationManager.sendMessageAndStream(userId, chatId, request))
          .isInstanceOf(DuplicateRequestException.class)
          .hasMessageContaining("already an active AI generation for this chat");
    }

    @Test
    @DisplayName("Should fail early if credit balance is zero")
    void shouldFailEarlyIfZeroCredit() {
      SendMessageRequest request = new SendMessageRequest("Hello AI", null);
      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(0, 5));

      assertThatThrownBy(() -> conversationManager.sendMessageAndStream(userId, chatId, request))
          .isInstanceOf(InsufficientCreditException.class);
    }

    @Test
    @DisplayName("Should save message after successful generation and trigger naming tasks if first message")
    void shouldSaveMessageAfterGeneration() {
      SendMessageRequest request = new SendMessageRequest("Test", null);
      ChatEntity mockChat = mock(ChatEntity.class);
      com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity projMock = mock(com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity.class);
      when(projMock.getId()).thenReturn(UUID.randomUUID());
      when(projMock.isAutoNamed()).thenReturn(true);
      when(mockChat.getConversationProject()).thenReturn(projMock);

      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(10, 0));
      when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "Test")).thenReturn(mockChat);
      when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(new ArrayList<>());
      when(messageRepository.countByChatId(chatId)).thenReturn(1L);

      Generation generation = new Generation(new org.springframework.ai.chat.messages.AssistantMessage("Hello World"));
      ChatResponse chatResponse = new ChatResponse(List.of(generation));

      when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse));

      conversationManager.sendMessageAndStream(userId, chatId, request);

      // Trigger stream completion in test
      // The flux should complete immediately and call saveAssistantMessageAndUnlock

      verify(chatMessageTransactionService).lockChatForGeneration(chatId, userId, "Test");
      verify(internalLightTaskService).suggestChatTitleAsync(userId, mockChat, "Test");
      verify(internalLightTaskService).suggestProjectNameAsync(userId, mockChat.getConversationProject().getId(), mockChat.getConversationProject().isAutoNamed(), "Test");

      // Wait a small amount for the async flux subscription to finish since reactor
      // might execute it on same thread for just()
      // In just() it executes on the subscriber thread.
      verify(chatMessageTransactionService).saveAssistantMessageAndUnlock(chatId, userId, "Hello World");
      verify(aiUsageCaptureService).capture(any(), any(), eq(com.arslanca.orchestrai.entity.enums.AiUsageSurface.CHAT),
          eq(com.arslanca.orchestrai.entity.enums.AiUsageEventStatus.COMPLETED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should defer naming tasks until successful completion for local test mode")
    void shouldDeferNamingTasksUntilSuccessfulCompletionForLocalTestMode() {
      SendMessageRequest request = new SendMessageRequest("Test", null);
      ChatEntity mockChat = mock(ChatEntity.class);
      com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity projMock = mock(com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity.class);
      UUID projectId = UUID.randomUUID();
      when(projMock.getId()).thenReturn(projectId);
      when(projMock.isAutoNamed()).thenReturn(true);
      when(mockChat.getConversationProject()).thenReturn(projMock);

      when(aiProviderRuntimeProperties.testModeEnabled()).thenReturn(true);
      when(aiProviderRuntimeProperties.isLocalBaseUrl("openai-compatible")).thenReturn(true);
      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(10, 0));
      when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "Test")).thenReturn(mockChat);
      when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(new ArrayList<>());
      when(messageRepository.countByChatId(chatId)).thenReturn(1L);

      Generation generation = new Generation(new org.springframework.ai.chat.messages.AssistantMessage("Hello World"));
      ChatResponse chatResponse = new ChatResponse(List.of(generation));
      when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse));

      conversationManager.sendMessageAndStream(userId, chatId, request);

      InOrder inOrder = inOrder(chatMessageTransactionService, internalLightTaskService);
      inOrder.verify(chatMessageTransactionService).lockChatForGeneration(chatId, userId, "Test");
      inOrder.verify(chatMessageTransactionService).saveAssistantMessageAndUnlock(chatId, userId, "Hello World");
      inOrder.verify(internalLightTaskService).suggestChatTitleAsync(userId, mockChat, "Test");
      inOrder.verify(internalLightTaskService).suggestProjectNameAsync(userId, projectId, true, "Test");
    }

    @Test
    @DisplayName("Should treat BLUEPRINT_READY as plain assistant text")
    void shouldTreatBlueprintReadyAsPlainAssistantText() {
      SendMessageRequest request = new SendMessageRequest("Build the blueprint", "cursor");

      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(10, 0));
      when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "Build the blueprint")).thenReturn(chat);
      when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(new ArrayList<>());
      when(messageRepository.countByChatId(chatId)).thenReturn(2L);

      Generation generation = new Generation(new org.springframework.ai.chat.messages.AssistantMessage("BLUEPRINT_READY"));
      ChatResponse chatResponse = new ChatResponse(List.of(generation));
      when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse));

      conversationManager.sendMessageAndStream(userId, chatId, request);

      verify(chatMessageTransactionService).saveAssistantMessageAndUnlock(chatId, userId, "BLUEPRINT_READY");
    }

    @Test
    @DisplayName("Should truncate context based on 8000 character limit (token limits)")
    void shouldTruncateContextWhenExceedingTokenLimits() {
      SendMessageRequest request = new SendMessageRequest("Test Token Limits", null);

      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(10, 0));
      when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "Test Token Limits")).thenReturn(chat);

      // Create 3 messages
      // Oldest: 1000 chars
      // Middle: 5000 chars
      // Newest: 4000 chars
      // Returned by repository descending (Newest first) -> Newest, Middle, Oldest
      MessageEntity oldest = MessageEntity.builder().role(MessageRole.USER).content("A".repeat(1000)).build();
      MessageEntity middle = MessageEntity.builder().role(MessageRole.ASSISTANT).content("B".repeat(5000)).build();
      MessageEntity newest = MessageEntity.builder().role(MessageRole.USER).content("C".repeat(4000)).build();

      List<MessageEntity> dbMessages = new ArrayList<>(List.of(newest, middle, oldest));

      when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(dbMessages);

      when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.empty());

      conversationManager.sendMessageAndStream(userId, chatId, request);

      ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
      verify(chatModel).stream(promptCaptor.capture());

      Prompt prompt = promptCaptor.getValue();
      List<Message> springAiMessages = prompt.getInstructions();

      // System message (1) + Newest message (1) = 2 messages
      // Middle message should be truncated because 4000 + 5000 = 9000 > 8000
      // Oldest won't be checked because limit exceeded
      assertThat(springAiMessages).hasSize(2);
      assertThat(springAiMessages.get(1).getText()).isEqualTo("C".repeat(4000));
    }

    @Test
    @DisplayName("Should force refund on provider failure even if partial tokens sent")
    void shouldForceRefundOnProviderFailure() {
      SendMessageRequest request = new SendMessageRequest("Test Provider Failure", null);

      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(10, 0));
      when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "Test Provider Failure")).thenReturn(chat);
      when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(new ArrayList<>());

      // Emit one token, then emit a 429 Error
      Generation generation = new Generation(new org.springframework.ai.chat.messages.AssistantMessage("Partial"));
      ChatResponse chatResponse = new ChatResponse(List.of(generation));
      
      Flux<ChatResponse> errorFlux = Flux.concat(
          Flux.just(chatResponse),
          Flux.error(new RuntimeException("429 Too Many Requests"))
      );

      when(chatModel.stream(any(Prompt.class))).thenReturn(errorFlux);

      conversationManager.sendMessageAndStream(userId, chatId, request);

      // Verify that unlockChatAndRefund(chatId, userId, true) is called instead of saveAssistantMessageAndUnlock
      verify(chatMessageTransactionService).unlockChatAndRefund(chatId, userId, true);
      verify(chatMessageTransactionService, never()).saveAssistantMessageAndUnlock(any(), any(), any());
      verify(aiUsageCaptureService).capture(any(), any(), eq(com.arslanca.orchestrai.entity.enums.AiUsageSurface.CHAT),
          eq(com.arslanca.orchestrai.entity.enums.AiUsageEventStatus.FAILED), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Should force refund on SSE timeout even if tokens were not generated")
    void shouldForceRefundOnSseTimeout() throws Exception {
      SendMessageRequest request = new SendMessageRequest("Test SSE Timeout", null);

      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(10, 0));
      when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "Test SSE Timeout")).thenReturn(chat);
      when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(new ArrayList<>());

      // Never complete or emit
      when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.never());

      org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = 
          conversationManager.sendMessageAndStream(userId, chatId, request);

      // Trigger timeout manually using reflection since SseEmitter doesn't expose timeout callback invocation directly
      java.lang.reflect.Field timeoutCallbackField = org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter.class.getDeclaredField("timeoutCallback");
      timeoutCallbackField.setAccessible(true);
      Runnable timeoutCallback = (Runnable) timeoutCallbackField.get(emitter);
      
      if (timeoutCallback != null) {
          timeoutCallback.run();
      }

      // Verify forced refund was triggered
      verify(chatMessageTransactionService).unlockChatAndRefund(chatId, userId, true);
    }

    @Test
    @DisplayName("Should capture failed chat usage when assistant persistence fails after provider completion")
    void shouldCaptureFailedUsageWhenAssistantPersistenceFails() {
      SendMessageRequest request = new SendMessageRequest("Test persistence failure", null);

      when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(10, 0));
      when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "Test persistence failure")).thenReturn(chat);
      when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(new ArrayList<>());
      when(messageRepository.countByChatId(chatId)).thenReturn(2L);

      Generation generation = new Generation(new org.springframework.ai.chat.messages.AssistantMessage("Hello World"));
      ChatResponse chatResponse = new ChatResponse(List.of(generation));
      when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(chatResponse));
      doThrow(new RuntimeException("db write failed"))
          .when(chatMessageTransactionService)
          .saveAssistantMessageAndUnlock(chatId, userId, "Hello World");

      conversationManager.sendMessageAndStream(userId, chatId, request);

      verify(chatMessageTransactionService).unlockChatAndRefund(chatId, userId, false);
      verify(aiUsageCaptureService).capture(any(), any(), eq(com.arslanca.orchestrai.entity.enums.AiUsageSurface.CHAT),
          eq(com.arslanca.orchestrai.entity.enums.AiUsageEventStatus.FAILED), any(), any(), any(), any(), any(), any(), any());
    }
  }

  @Nested
  @DisplayName("Project and Chat Naming Logic Tests")
  class ProjectAndChatNamingTests {

    @Test
    @DisplayName("Should create project with default placeholder and isAutoNamed=true when name is blank")
    void shouldCreateAutoNamedProjectWhenNameIsBlank() {
        com.arslanca.orchestrai.entity.concretes.UserEntity user = mock(com.arslanca.orchestrai.entity.concretes.UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        ArgumentCaptor<com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity> captor = 
            ArgumentCaptor.forClass(com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity.class);

        when(projectRepository.save(captor.capture())).thenAnswer(i -> {
            com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity p = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });

        com.arslanca.orchestrai.business.dtos.conversation.CreateProjectRequest req = 
            new com.arslanca.orchestrai.business.dtos.conversation.CreateProjectRequest("");

        conversationManager.createProject(userId, req);

        com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Untitled Project");
        assertThat(saved.isAutoNamed()).isTrue();
    }

    @Test
    @DisplayName("Should keep provided name and isAutoNamed=false when creating project")
    void shouldCreateManuallyNamedProject() {
        com.arslanca.orchestrai.entity.concretes.UserEntity user = mock(com.arslanca.orchestrai.entity.concretes.UserEntity.class);
        when(userRepository.findById(userId)).thenReturn(java.util.Optional.of(user));

        ArgumentCaptor<com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity> captor = 
            ArgumentCaptor.forClass(com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity.class);

        when(projectRepository.save(captor.capture())).thenAnswer(i -> {
            com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity p = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(p, "id", UUID.randomUUID());
            return p;
        });

        com.arslanca.orchestrai.business.dtos.conversation.CreateProjectRequest req = 
            new com.arslanca.orchestrai.business.dtos.conversation.CreateProjectRequest("My Custom SaaS");

        conversationManager.createProject(userId, req);

        com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("My Custom SaaS");
        assertThat(saved.isAutoNamed()).isFalse();
    }

    @Test
    @DisplayName("Should create chat as auto named placeholder without requesting title")
    void shouldCreateAutoNamedChat() {
        com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity project = 
            com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity.builder().id(UUID.randomUUID()).name("P").isAutoNamed(false).build();
        when(projectRepository.findByIdAndUserId(project.getId(), userId)).thenReturn(java.util.Optional.of(project));

        ArgumentCaptor<ChatEntity> captor = ArgumentCaptor.forClass(ChatEntity.class);

        when(chatRepository.save(captor.capture())).thenAnswer(i -> {
            ChatEntity c = i.getArgument(0);
            org.springframework.test.util.ReflectionTestUtils.setField(c, "id", UUID.randomUUID());
            return c;
        });

        com.arslanca.orchestrai.business.dtos.conversation.CreateChatRequest req = 
            new com.arslanca.orchestrai.business.dtos.conversation.CreateChatRequest();

        conversationManager.createChat(userId, project.getId(), req);

        ChatEntity saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo("New Chat");
        assertThat(saved.isAutoNamed()).isTrue();
    }
  }
}
