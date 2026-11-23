package com.arslanca.orchestrai.business.runtime;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import org.springframework.ai.chat.model.ChatModel;

public record ResolvedHeavyModelRuntimeBinding(
        ResolvedHeavyModel resolvedHeavyModel,
        HeavyProviderAdapter heavyProviderAdapter,
        ProviderCredential providerCredential,
        AiUsageRuntimeDescriptor runtimeDescriptor) {

    public ResolvedHeavyModelRuntimeBinding(
            final ResolvedHeavyModel resolvedHeavyModel,
            final HeavyProviderAdapter heavyProviderAdapter,
            final ProviderCredential providerCredential) {
        this(
                resolvedHeavyModel,
                heavyProviderAdapter,
                providerCredential,
                resolvedHeavyModel == null ? null : AiUsageRuntimeDescriptors.forResolvedHeavyModel(resolvedHeavyModel, providerCredential));
    }

    public ChatModel buildChatModel() {
        return heavyProviderAdapter.buildHeavyChatModel(resolvedHeavyModel, providerCredential);
    }

    public ResolvedAiExecutionRoute toExecutionRoute(
            final AiConnectionExecutionClass executionClass,
            final ChatModel chatModel) {
        return new ResolvedAiExecutionRoute(
                executionClass,
                chatModel,
                resolvedHeavyModel,
                providerCredential,
                runtimeDescriptor);
    }
}
