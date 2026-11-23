package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.AiExecutionRouteResolverService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.runtime.AiUsageRuntimeDescriptor;
import com.arslanca.orchestrai.business.runtime.AiUsageRuntimeDescriptors;
import com.arslanca.orchestrai.business.runtime.ResolvedAiExecutionRoute;
import com.arslanca.orchestrai.core.ai.provider.ApprovedHeavyModelRegistry;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.PlatformCredentialRegistry;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.core.config.AiModelProperties;
import com.arslanca.orchestrai.core.config.AiProviderRuntimeProperties;
import com.arslanca.orchestrai.core.qualifier.LightModel;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AiExecutionRouteResolverManager implements AiExecutionRouteResolverService {

    private final HeavyModelResolutionService heavyModelResolutionService;
    private final HeavyModelCredentialResolver heavyModelCredentialResolver;
    private final ChatModel lightChatModel;
    private final AiModelProperties aiModelProperties;
    private final AiProviderRuntimeProperties aiProviderRuntimeProperties;
    private final PlatformCredentialRegistry platformCredentialRegistry;

    public AiExecutionRouteResolverManager(
            final HeavyModelResolutionService heavyModelResolutionService,
            final HeavyModelCredentialResolver heavyModelCredentialResolver,
            @LightModel final ChatModel lightChatModel,
            final AiModelProperties aiModelProperties,
            final AiProviderRuntimeProperties aiProviderRuntimeProperties,
            final PlatformCredentialRegistry platformCredentialRegistry) {
        this.heavyModelResolutionService = heavyModelResolutionService;
        this.heavyModelCredentialResolver = heavyModelCredentialResolver;
        this.lightChatModel = lightChatModel;
        this.aiModelProperties = aiModelProperties;
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
        this.platformCredentialRegistry = platformCredentialRegistry;
    }

    @Override
    public ResolvedAiExecutionRoute resolve(
            final UUID userId,
            final String requestedHeavyModelKey,
            final CredentialSource requestedAuthSource) {
        final ResolvedHeavyModel resolvedHeavyModel = heavyModelResolutionService.resolve(userId, requestedHeavyModelKey, requestedAuthSource);
        if (resolvedHeavyModel.executionClass() == AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE) {
            return new ResolvedAiExecutionRoute(
                    AiConnectionExecutionClass.PLATFORM_DUAL_ROUTE,
                    lightChatModel,
                    resolvedHeavyModel,
                    null,
                    platformLightRuntimeDescriptor());
        }

        final ProviderCredential providerCredential = heavyModelCredentialResolver.resolve(resolvedHeavyModel);
        final HeavyProviderAdapter heavyProviderAdapter = heavyModelResolutionService.adapterFor(resolvedHeavyModel);
        return new ResolvedAiExecutionRoute(
                AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                heavyProviderAdapter.buildHeavyChatModel(resolvedHeavyModel, providerCredential),
                resolvedHeavyModel,
                providerCredential,
                runtimeDescriptorForUserRoute(resolvedHeavyModel, providerCredential));
    }

    private AiUsageRuntimeDescriptor platformLightRuntimeDescriptor() {
        final boolean localLightModel = shouldUseLocalLightModel();
        final String providerKey = localLightModel ? "openai-compatible" : "google";
        return AiUsageRuntimeDescriptors.forPlatformLightModel(
                providerKey,
                normalizeModelKey(aiModelProperties.lightModel()),
                aiModelProperties.lightModel());
    }

    private AiUsageRuntimeDescriptor runtimeDescriptorForUserRoute(
            final ResolvedHeavyModel resolvedHeavyModel,
            final ProviderCredential providerCredential) {
        return AiUsageRuntimeDescriptors.forResolvedHeavyModel(resolvedHeavyModel, providerCredential);
    }

    private boolean shouldUseLocalLightModel() {
        return aiProviderRuntimeProperties.testModeEnabled()
                && aiProviderRuntimeProperties.isLocalBaseUrl("openai-compatible")
                && platformCredentialRegistry.findPlatformCredential("openai-compatible").isPresent()
                && ApprovedHeavyModelRegistry.normalize("openai-compatible", aiModelProperties.lightModel()).isPresent();
    }

    private String normalizeModelKey(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Runtime model key could not be resolved.");
        }
        final String trimmed = value.trim();
        final int separatorIndex = trimmed.indexOf(':');
        return separatorIndex > -1 ? trimmed.substring(separatorIndex + 1) : trimmed;
    }
}
