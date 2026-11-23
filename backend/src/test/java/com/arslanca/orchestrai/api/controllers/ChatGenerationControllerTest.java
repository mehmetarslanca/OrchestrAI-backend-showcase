package com.arslanca.orchestrai.api.controllers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatGenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatPlanningStatusResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.enums.ChatPlanningReadinessStatus;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import com.arslanca.orchestrai.entity.enums.UserRole;

@ExtendWith(MockitoExtension.class)
class ChatGenerationControllerTest {

    @Mock
    private ProjectGenerationPreparationService generationPreparationService;

    private ChatGenerationController controller;
    private AuthenticatedUser authenticatedUser;

    @BeforeEach
    void setUp() {
        controller = new ChatGenerationController(generationPreparationService);
        authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), "memet", UserRole.USER, null);
    }

    @Test
    @DisplayName("prepareGenerationFromChat: sadece preparation response doner")
    void shouldPrepareGenerationFromChat() {
        final UUID chatId = UUID.randomUUID();
        final ChatGenerationPreparationRequest request = new ChatGenerationPreparationRequest("cursor");
        final GenerationPreparationResponse response = new GenerationPreparationResponse(
                UUID.randomUUID(),
                "Planning Workspace",
                "cursor",
                null,
                List.of(),
                false,
                null);

        when(generationPreparationService.prepareGenerationFromChat(authenticatedUser.id(), chatId, request)).thenReturn(response);

        final var entity = controller.prepareGenerationFromChat(authenticatedUser, chatId, request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isEqualTo(response);
        verify(generationPreparationService).prepareGenerationFromChat(authenticatedUser.id(), chatId, request);
    }

    @Test
    @DisplayName("getChatPlanningStatus: only returns readiness state")
    void shouldReturnChatPlanningStatus() {
        final UUID chatId = UUID.randomUUID();
        final ChatPlanningStatusResponse response = new ChatPlanningStatusResponse(
                true,
                ChatPlanningReadinessStatus.READY_FOR_PREPARATION,
                "Planning is ready.",
                null);

        when(generationPreparationService.getChatPlanningStatus(authenticatedUser.id(), chatId)).thenReturn(response);

        final var entity = controller.getChatPlanningStatus(authenticatedUser, chatId);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isEqualTo(response);
        verify(generationPreparationService).getChatPlanningStatus(authenticatedUser.id(), chatId);
    }

    @Test
    @DisplayName("retryChatPlanningStatus: returns queued planning state")
    void shouldRetryChatPlanningStatus() {
        final UUID chatId = UUID.randomUUID();
        final ChatGenerationPreparationRequest request = new ChatGenerationPreparationRequest("cursor");
        final ChatPlanningStatusResponse response = ChatPlanningStatusResponse.analyzing("Analyzing the latest planning handoff.");

        when(generationPreparationService.retryChatPlanningAfterAssistantTurn(authenticatedUser.id(), chatId, "cursor"))
                .thenReturn(response);

        final var entity = controller.retryChatPlanningStatus(authenticatedUser, chatId, request);

        assertThat(entity.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(entity.getBody()).isEqualTo(response);
        verify(generationPreparationService).retryChatPlanningAfterAssistantTurn(authenticatedUser.id(), chatId, "cursor");
    }
}
