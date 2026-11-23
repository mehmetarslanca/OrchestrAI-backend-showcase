package com.arslanca.orchestrai.business.runtime;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass;
import org.springframework.ai.chat.model.ChatModel;

public record ResolvedAiExecutionRoute(
        AiConnectionExecutionClass executionClass,
        ChatModel chatModel,
        ResolvedHeavyModel resolvedHeavyModel,
        ProviderCredential providerCredential,
        AiUsageRuntimeDescriptor runtimeDescriptor) {

    public ResolvedAiExecutionRoute(
            final AiConnectionExecutionClass executionClass,
            final ChatModel chatModel,
            final ResolvedHeavyModel resolvedHeavyModel,
            final ProviderCredential providerCredential) {
        this(executionClass, chatModel, resolvedHeavyModel, providerCredential, null);
    }
}
