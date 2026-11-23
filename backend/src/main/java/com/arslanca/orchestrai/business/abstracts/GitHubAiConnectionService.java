package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackRequest;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackResponse;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCompletionRequest;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthStartResponse;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;

import java.util.UUID;

public interface GitHubAiConnectionService {

    GitHubAiOAuthStartResponse startAuthorization(UUID userId, String surface);

    GitHubAiOAuthCallbackResponse handleCallback(GitHubAiOAuthCallbackRequest request);

    ProviderCredentialStatusResponse completeAuthorization(UUID userId, GitHubAiOAuthCompletionRequest request);

    void revokeConnection(UUID userId, String surface);
}
