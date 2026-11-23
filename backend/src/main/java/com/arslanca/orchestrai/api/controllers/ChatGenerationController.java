package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.ProjectGenerationPreparationService;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatGenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatPlanningStatusResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/conversations/chats")
public class ChatGenerationController {

    private static final Logger log = LoggerFactory.getLogger(ChatGenerationController.class);

    private final ProjectGenerationPreparationService generationPreparationService;

    public ChatGenerationController(final ProjectGenerationPreparationService generationPreparationService) {
        this.generationPreparationService = generationPreparationService;
    }

    @PostMapping("/{chatId}/prepare-generation")
    public ResponseEntity<GenerationPreparationResponse> prepareGenerationFromChat(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final UUID chatId,
            @Valid @RequestBody final ChatGenerationPreparationRequest request) {
        log.info("Preparing generation from chat: userId={}, chatId={}", user.id(), chatId);
        final GenerationPreparationResponse response = generationPreparationService.prepareGenerationFromChat(user.id(), chatId, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{chatId}/planning-status")
    public ResponseEntity<ChatPlanningStatusResponse> getChatPlanningStatus(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final UUID chatId) {
        log.info("Checking planning status from chat: userId={}, chatId={}", user.id(), chatId);
        final ChatPlanningStatusResponse response = generationPreparationService.getChatPlanningStatus(user.id(), chatId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/{chatId}/planning-status/retry")
    public ResponseEntity<ChatPlanningStatusResponse> retryChatPlanningStatus(
            @AuthenticationPrincipal final AuthenticatedUser user,
            @PathVariable final UUID chatId,
            @Valid @RequestBody final ChatGenerationPreparationRequest request) {
        log.info("Retrying planning status from chat: userId={}, chatId={}", user.id(), chatId);
        final ChatPlanningStatusResponse response = generationPreparationService.retryChatPlanningAfterAssistantTurn(
                user.id(),
                chatId,
                request.ideTarget());
        return ResponseEntity.ok(response);
    }
}
