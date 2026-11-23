package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.ConversationService;
import com.arslanca.orchestrai.business.dtos.conversation.*;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * Kullanıcı bazlı konuşma (chat) sistemi endpoint'leri.
 */
@RestController
@RequestMapping("/api/v1/conversations")
@Slf4j
public class ConversationController {

    private final ConversationService conversationService;

    public ConversationController(ConversationService conversationService) {
        this.conversationService = conversationService;
    }


    // ---- Proje ----

    @GetMapping("/projects")
    public ResponseEntity<List<ConversationProjectResponse>> getUserProjects(
            @AuthenticationPrincipal AuthenticatedUser user) {
        log.info("API GET /projects requested by user: {}", user.id());
        return ResponseEntity.ok(conversationService.getUserProjects(user.id()));
    }

    @PostMapping("/projects")
    public ResponseEntity<ConversationProjectResponse> createProject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @Valid @RequestBody CreateProjectRequest request) {
        log.info("API POST /projects requested by user: {}", user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                conversationService.createProject(user.id(), request));
    }

    @DeleteMapping("/projects/{projectId}")
    public ResponseEntity<Void> deleteProject(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId) {
        log.info("API DELETE /projects/{} requested by user: {}", projectId, user.id());
        conversationService.deleteProject(user.id(), projectId);
        return ResponseEntity.noContent().build();
    }

    // ---- Chat ----

    @GetMapping("/projects/{projectId}/chats")
    public ResponseEntity<List<ChatResponse>> getProjectChats(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId) {
        log.info("API GET /projects/{}/chats requested by user: {}", projectId, user.id());
        return ResponseEntity.ok(conversationService.getProjectChats(user.id(), projectId));
    }

    @PostMapping("/projects/{projectId}/chats")
    public ResponseEntity<ChatResponse> createChat(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID projectId,
            @Valid @RequestBody CreateChatRequest request) {
        log.info("API POST /projects/{}/chats requested by user: {}", projectId, user.id());
        return ResponseEntity.status(HttpStatus.CREATED).body(
                conversationService.createChat(user.id(), projectId, request));
    }

    @DeleteMapping("/chats/{chatId}")
    public ResponseEntity<Void> deleteChat(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID chatId) {
        log.info("API DELETE /chats/{} requested by user: {}", chatId, user.id());
        conversationService.deleteChat(user.id(), chatId);
        return ResponseEntity.noContent().build();
    }

    // ---- Messages ----

    @GetMapping("/chats/{chatId}/messages")
    public ResponseEntity<List<MessageResponse>> getChatMessages(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID chatId) {
        log.info("API GET /chats/{}/messages requested by user: {}", chatId, user.id());
        return ResponseEntity.ok(conversationService.getChatMessages(user.id(), chatId));
    }

    @PostMapping(value = "/chats/{chatId}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessage(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID chatId,
            @Valid @RequestBody SendMessageRequest request) {
        log.info("API POST /chats/{}/messages (SSE) requested by user: {}", chatId, user.id());
        return conversationService.sendMessageAndStream(user.id(), chatId, request);
    }

    @PostMapping("/chats/{chatId}/stop")
    public ResponseEntity<Void> stopStream(
            @AuthenticationPrincipal AuthenticatedUser user,
            @PathVariable UUID chatId) {
        log.info("API POST /chats/{}/stop requested by user: {}", chatId, user.id());
        conversationService.stopStream(user.id(), chatId);
        return ResponseEntity.accepted().build();
    }
}
