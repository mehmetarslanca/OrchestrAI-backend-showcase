package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.concretes.AiSettingsManager;
import com.arslanca.orchestrai.business.concretes.ProviderCredentialManager;
import com.arslanca.orchestrai.business.dtos.ai.AiConnectionDescriptor;
import com.arslanca.orchestrai.business.dtos.ai.AiConnectionState;
import com.arslanca.orchestrai.business.dtos.ai.AvailableProviderDescriptor;
import com.arslanca.orchestrai.business.dtos.ai.AiSettingsResponse;
import com.arslanca.orchestrai.business.dtos.ai.AiSettingsUpdateRequest;
import com.arslanca.orchestrai.business.dtos.ai.ProviderCredentialStatusResponse;
import com.arslanca.orchestrai.business.dtos.ai.ProviderKeyRequest;
import com.arslanca.orchestrai.business.dtos.ai.SelectedModelSettingsResponse;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import com.arslanca.orchestrai.entity.enums.AiConnectionAuthMethod;
import com.arslanca.orchestrai.entity.enums.AiConnectionBillingMode;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import com.arslanca.orchestrai.entity.enums.AiConnectionStatus;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import com.arslanca.orchestrai.entity.enums.ProviderCredentialStatus;
import com.arslanca.orchestrai.entity.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AiSettingsControllerTest {

    @Mock
    private AiSettingsManager aiSettingsManager;

    @Mock
    private ProviderCredentialManager providerCredentialManager;

    private AiSettingsController controller;
    private AuthenticatedUser authenticatedUser;

    @BeforeEach
    void setUp() {
        controller = new AiSettingsController(aiSettingsManager, providerCredentialManager);
        authenticatedUser = new AuthenticatedUser(UUID.randomUUID(), "memet", UserRole.USER, null);
    }

    @Test
    void shouldReturnAiSettingsForAuthenticatedUser() {
        UUID connectionId = UUID.randomUUID();
        AiSettingsResponse response = new AiSettingsResponse(
                "openai:gpt-5.4",
                CredentialSource.BYOK,
                true,
                List.of(new AvailableProviderDescriptor("openai", "OpenAI", false, false, List.of())),
                List.of(new ProviderCredentialStatusResponse("openai", ProviderCredentialStatus.VALID, "sk-o...5678", null, "OpenAI", false, false, null, null, null, null,
                        connectionId, AiConnectionType.OPENAI_BYOK, AiConnectionStatus.VALID, AiConnectionAuthMethod.API_KEY, AiConnectionBillingMode.BYOK, List.of())),
                new SelectedModelSettingsResponse("openai:gpt-5.4", CredentialSource.BYOK, connectionId, AiConnectionExecutionClass.USER_SINGLE_ROUTE, true),
                List.of(new AiConnectionDescriptor(
                        "OPENAI_BYOK",
                        "openai",
                        "OpenAI API Key",
                        "openai",
                        CredentialSource.BYOK,
                        AiConnectionAuthMethod.API_KEY,
                        AiConnectionBillingMode.BYOK,
                        AiConnectionState.CONNECTED,
                        true,
                        true,
                        true,
                        false,
                        connectionId,
                        AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                        true,
                        List.of())));
        when(aiSettingsManager.getSettings(authenticatedUser.id())).thenReturn(response);

        var entity = controller.getAiSettings(authenticatedUser);

        assertThat(entity.getBody()).isEqualTo(response);
    }

    @Test
    void shouldUpdateAiSettings() {
        UUID connectionId = UUID.randomUUID();
        AiSettingsUpdateRequest request = new AiSettingsUpdateRequest("openai:gpt-5.4", CredentialSource.BYOK, "openai:gpt-5.4", connectionId);
        AiSettingsResponse response = new AiSettingsResponse("openai:gpt-5.4", CredentialSource.BYOK, false, List.of(), List.of());
        when(aiSettingsManager.updateSettings(authenticatedUser.id(), request)).thenReturn(response);

        var entity = controller.updateAiSettings(authenticatedUser, request);

        assertThat(entity.getBody()).isEqualTo(response);
    }

    @Test
    void shouldSaveAndDeleteProviderKeys() {
        ProviderKeyRequest request = new ProviderKeyRequest("sk-openai-1234");
        ProviderCredentialStatusResponse response = new ProviderCredentialStatusResponse(
                "openai",
                ProviderCredentialStatus.VALID,
                "sk-o...1234",
                null,
                "OpenAI",
                false,
                false,
                null,
                null,
                null,
                null,
                List.of());
        when(providerCredentialManager.saveCredential(authenticatedUser.id(), "openai", request.apiKey())).thenReturn(response);
        doNothing().when(providerCredentialManager).revokeCredential(authenticatedUser.id(), "openai");

        var saveEntity = controller.saveProviderKey(authenticatedUser, "openai", request);
        var deleteEntity = controller.deleteProviderKey(authenticatedUser, "openai");

        assertThat(saveEntity.getBody()).isEqualTo(response);
        assertThat(deleteEntity.getStatusCode().value()).isEqualTo(204);
        verify(providerCredentialManager).revokeCredential(authenticatedUser.id(), "openai");
    }
}
