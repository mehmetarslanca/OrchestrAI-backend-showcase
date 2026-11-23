package com.arslanca.orchestrai.core.ai.provider;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Locale;

public interface HeavyProviderAdapter {

    String providerKey();

    boolean supportsPlatformCredential();

    List<DiscoveredHeavyModel> listAvailableHeavyModels(ProviderCredential credential);

    ChatModel buildHeavyChatModel(ResolvedHeavyModel resolvedModel, ProviderCredential credential);

    void validateCredential(ProviderCredential credential);

    default boolean supports(final ResolvedHeavyModel resolvedModel) {
        return resolvedModel != null && providerKey().equals(normalizeProviderKey(resolvedModel.provider()));
    }

    default boolean supports(final ProviderCredential credential) {
        return credential != null && providerKey().equals(normalizeProviderKey(credential.providerKey()));
    }

    private static String normalizeProviderKey(final String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
