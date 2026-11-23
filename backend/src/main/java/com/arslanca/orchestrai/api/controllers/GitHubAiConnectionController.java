package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.GitHubAiConnectionService;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackRequest;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackResponse;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCompletionRequest;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthStartResponse;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class GitHubAiConnectionController {

    private final GitHubAiConnectionService gitHubAiConnectionService;

    public GitHubAiConnectionController(final GitHubAiConnectionService gitHubAiConnectionService) {
        this.gitHubAiConnectionService = gitHubAiConnectionService;
    }

    @PostMapping("/users/me/ai-connections/github/{surface}/oauth/authorize")
    public ResponseEntity<GitHubAiOAuthStartResponse> startAuthorization(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @PathVariable final String surface) {
        return ResponseEntity.ok(gitHubAiConnectionService.startAuthorization(authenticatedUser.id(), surface));
    }

    @PostMapping("/ai-connections/github/oauth/callback")
    public ResponseEntity<GitHubAiOAuthCallbackResponse> handleCallback(
            @RequestBody final GitHubAiOAuthCallbackRequest request) {
        return ResponseEntity.ok(gitHubAiConnectionService.handleCallback(request));
    }

    @PostMapping("/users/me/ai-connections/github/oauth/complete")
    public ResponseEntity<ProviderCredentialStatusResponse> completeAuthorization(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @Valid @RequestBody final GitHubAiOAuthCompletionRequest request) {
        return ResponseEntity.ok(gitHubAiConnectionService.completeAuthorization(authenticatedUser.id(), request));
    }

    @DeleteMapping("/users/me/ai-connections/github/{surface}")
    public ResponseEntity<Void> revokeConnection(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @PathVariable final String surface) {
        gitHubAiConnectionService.revokeConnection(authenticatedUser.id(), surface);
        return ResponseEntity.noContent().build();
    }
}
