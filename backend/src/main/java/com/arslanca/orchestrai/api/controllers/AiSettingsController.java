package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.concretes.AiSettingsManager;
import com.arslanca.orchestrai.business.concretes.ProviderCredentialManager;
import com.arslanca.orchestrai.business.dtos.ai.AiSettingsResponse;
import com.arslanca.orchestrai.business.dtos.ai.AiSettingsUpdateRequest;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.business.dtos.ai.ProviderKeyRequest;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/users/me")
public class AiSettingsController {

    private final AiSettingsManager aiSettingsManager;
    private final ProviderCredentialManager providerCredentialManager;

    public AiSettingsController(
            final AiSettingsManager aiSettingsManager,
            final ProviderCredentialManager providerCredentialManager) {
        this.aiSettingsManager = aiSettingsManager;
        this.providerCredentialManager = providerCredentialManager;
    }

    @GetMapping("/ai-settings")
    public ResponseEntity<AiSettingsResponse> getAiSettings(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser) {
        return ResponseEntity.ok(aiSettingsManager.getSettings(authenticatedUser.id()));
    }

    @PutMapping("/ai-settings")
    public ResponseEntity<AiSettingsResponse> updateAiSettings(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @Valid @RequestBody final AiSettingsUpdateRequest request) {
        return ResponseEntity.ok(aiSettingsManager.updateSettings(authenticatedUser.id(), request));
    }

    @PutMapping("/provider-keys/{provider}")
    public ResponseEntity<ProviderCredentialStatusResponse> saveProviderKey(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @PathVariable final String provider,
            @Valid @RequestBody final ProviderKeyRequest request) {
        return ResponseEntity.ok(providerCredentialManager.saveCredential(authenticatedUser.id(), provider, request.apiKey()));
    }

    @DeleteMapping("/provider-keys/{provider}")
    public ResponseEntity<Void> deleteProviderKey(
            @AuthenticationPrincipal final AuthenticatedUser authenticatedUser,
            @PathVariable final String provider) {
        providerCredentialManager.revokeCredential(authenticatedUser.id(), provider);
        return ResponseEntity.noContent().build();
    }
}
