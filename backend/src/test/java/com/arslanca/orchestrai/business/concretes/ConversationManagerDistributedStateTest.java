package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.CreditManagementService;
import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.abstracts.AiExecutionRouteResolverService;
import com.arslanca.orchestrai.business.abstracts.AiUsageCaptureService;
import com.arslanca.orchestrai.business.planning.preparation.TransientChatPlanningHandoffRegistry;
import com.arslanca.orchestrai.business.dtos.conversation.SendMessageRequest;
import com.arslanca.orchestrai.business.dtos.credit.CreditResponse;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ConversationProjectRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConversationManagerDistributedStateTest {

    @Mock private ConversationProjectRepository projectRepository;
    @Mock private ChatRepository chatRepository;
    @Mock private MessageRepository messageRepository;
    @Mock private UserRepository userRepository;
    @Mock private CreditManagementService creditManagementService;
    @Mock private ChatMessageTransactionService chatMessageTransactionService;
    @Mock private DistributedChatStreamRegistry chatStreamRegistry;
    @Mock private ChatModel chatModel;
    @Mock private AiProviderRuntimeProperties aiProviderRuntimeProperties;
    @Mock private AiUsageCaptureService aiUsageCaptureService;
    @Mock private ProjectGenerationPreparationService generationPreparationService;
    @Mock private TransientChatPlanningHandoffRegistry planningHandoffRegistry;

    @Test
    void shouldRegisterAndClearDistributedStreamStateDuringSuccessfulStreaming() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();
        com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity project =
                com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity.builder()
                        .id(UUID.randomUUID())
                        .name("Untitled Project")
                        .isAutoNamed(false)
                        .build();
        ChatEntity chat = ChatEntity.builder()
                .id(chatId)
                .conversationProject(project)
                .build();

        when(creditManagementService.getCreditBalance(userId)).thenReturn(new CreditResponse(10, 0));
        when(chatMessageTransactionService.lockChatForGeneration(chatId, userId, "hello")).thenReturn(chat);
        when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(new ArrayList<>());
        when(chatModel.stream(any(Prompt.class))).thenReturn(Flux.just(new ChatResponse(List.of(
                new Generation(new org.springframework.ai.chat.messages.AssistantMessage("world"))))));
        AiExecutionRouteResolverService routeResolverService = mock(AiExecutionRouteResolverService.class);
        when(routeResolverService.resolve(any(), any(), any())).thenReturn(
                new ResolvedAiExecutionRoute(AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE, chatModel, null, null));

        ConversationManager manager = new ConversationManager(
                projectRepository,
                chatRepository,
                messageRepository,
                userRepository,
                creditManagementService,
                chatMessageTransactionService,
                chatStreamRegistry,
                routeResolverService,
                aiUsageCaptureService,
                aiProviderRuntimeProperties,
                generationPreparationService,
                planningHandoffRegistry,
                mock(com.arslanca.orchestrai.business.abstracts.InternalLightTaskService.class),
                "test-light-model");

        manager.sendMessageAndStream(userId, chatId, new SendMessageRequest("hello", null));

        verify(chatStreamRegistry).registerStream(chatId, userId);
        verify(chatStreamRegistry).clearStream(chatId);
    }

    @Test
    void shouldSignalDistributedCancelWhenLocalNodeDoesNotOwnActiveStream() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(mock(ChatEntity.class)));
        when(chatStreamRegistry.hasActiveStream(chatId)).thenReturn(true);
        AiExecutionRouteResolverService routeResolverService = mock(AiExecutionRouteResolverService.class);

        ConversationManager manager = new ConversationManager(
                projectRepository,
                chatRepository,
                messageRepository,
                userRepository,
                creditManagementService,
                chatMessageTransactionService,
                chatStreamRegistry,
                routeResolverService,
                aiUsageCaptureService,
                aiProviderRuntimeProperties,
                generationPreparationService,
                planningHandoffRegistry,
                mock(com.arslanca.orchestrai.business.abstracts.InternalLightTaskService.class),
                "test-light-model");

        manager.stopStream(userId, chatId);

        verify(chatStreamRegistry).requestCancel(chatId, userId);
    }

    @Test
    void shouldResolveOwnerUserIdWithoutLoadingLazyConversationProject() {
        UUID userId = UUID.randomUUID();
        UUID chatId = UUID.randomUUID();

        when(chatStreamRegistry.getOwnerUserId(chatId)).thenReturn(null);
        when(chatRepository.findOwnerUserIdByChatId(chatId)).thenReturn(Optional.of(userId));
        AiExecutionRouteResolverService routeResolverService = mock(AiExecutionRouteResolverService.class);

        ConversationManager manager = new ConversationManager(
                projectRepository,
                chatRepository,
                messageRepository,
                userRepository,
                creditManagementService,
                chatMessageTransactionService,
                chatStreamRegistry,
                routeResolverService,
                aiUsageCaptureService,
                aiProviderRuntimeProperties,
                generationPreparationService,
                planningHandoffRegistry,
                mock(com.arslanca.orchestrai.business.abstracts.InternalLightTaskService.class),
                "test-light-model");

        manager.internalStopStreamWithoutUserId(chatId);

        verify(chatRepository).findOwnerUserIdByChatId(chatId);
        verify(chatStreamRegistry).clearStream(chatId);
    }
}
