package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.GitHubAiConnectionService;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackResponse;
import com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthStartResponse;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GitHubAiConnectionControllerTest {

    @Test
    void shouldDelegateAuthorizeAndCallbackAndCompleteFlows() {
        GitHubAiConnectionService service = mock(GitHubAiConnectionService.class);
        GitHubAiConnectionController controller = new GitHubAiConnectionController(service);
        UUID userId = UUID.randomUUID();
        com.arslanca.orchestrai.core.security.AuthenticatedUser user = new com.arslanca.orchestrai.core.security.AuthenticatedUser(
                userId,
                "memet",
                com.arslanca.orchestrai.entity.enums.UserRole.USER,
                null);

        when(service.startAuthorization(userId, "models")).thenReturn(new GitHubAiOAuthStartResponse(
                "github",
                "models",
                AiConnectionType.GITHUB_MODELS_USER_LINKED,
                "https://github.com/login/oauth/authorize",
                "state",
                OffsetDateTime.now().plusMinutes(10)));
        when(service.handleCallback(new com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackRequest("code", "state", null, null)))
                .thenReturn(new GitHubAiOAuthCallbackResponse(
                        "github",
                        "models",
                        AiConnectionType.GITHUB_MODELS_USER_LINKED,
                        "completion-token",
                        "42",
                        "octocat",
                        OffsetDateTime.now().plusMinutes(10)));
        when(service.completeAuthorization(userId, new com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCompletionRequest("completion-token")))
                .thenReturn(new ProviderCredentialStatusResponse(
                        "github",
                        ProviderCredentialStatus.VALID,
                        null,
                        OffsetDateTime.now(),
                        "GitHub Models",
                        false,
                        false,
                        null,
                        "github",
                        "github-models",
                        "OAUTH_USER_LINKED",
                        UUID.randomUUID(),
                        AiConnectionType.GITHUB_MODELS_USER_LINKED,
                        AiConnectionStatus.VALID,
                        com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod.OAUTH,
                        AiConnectionBillingMode.USER_LINKED,
                        List.of("USER_LINKED", "OAUTH", "DEFERRED")));

        assertThat(controller.startAuthorization(user, "models").getBody().connectionType())
                .isEqualTo(AiConnectionType.GITHUB_MODELS_USER_LINKED);
        assertThat(controller.handleCallback(new com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCallbackRequest("code", "state", null, null))
                .getBody()
                .completionToken()).isEqualTo("completion-token");
        assertThat(controller.completeAuthorization(user, new com.arslanca.orchestrai.business.dtos.ai.GitHubAiOAuthCompletionRequest("completion-token"))
                .getBody()
                .connectionType()).isEqualTo(AiConnectionType.GITHUB_MODELS_USER_LINKED);

        controller.revokeConnection(user, "models");
        verify(service).revokeConnection(userId, "models");
    }
}
