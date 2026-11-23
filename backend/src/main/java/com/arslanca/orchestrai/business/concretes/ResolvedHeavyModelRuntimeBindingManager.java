package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.runtime.ResolvedHeavyModelRuntimeBinding;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;
import com.arslanca.orchestrai.core.ai.provider.ProviderCredential;
import org.springframework.stereotype.Service;

@Service
public class ResolvedHeavyModelRuntimeBindingManager {

    private final HeavyModelResolutionService heavyModelResolutionService;
    private final HeavyModelCredentialResolver heavyModelCredentialResolver;

    public ResolvedHeavyModelRuntimeBindingManager(
            final HeavyModelResolutionService heavyModelResolutionService,
            final HeavyModelCredentialResolver heavyModelCredentialResolver) {
        this.heavyModelResolutionService = heavyModelResolutionService;
        this.heavyModelCredentialResolver = heavyModelCredentialResolver;
    }

    public ResolvedHeavyModelRuntimeBinding bind(final ResolvedHeavyModel resolvedHeavyModel) {
        final HeavyProviderAdapter heavyProviderAdapter = heavyModelResolutionService.adapterFor(resolvedHeavyModel);
        final ProviderCredential providerCredential = heavyModelCredentialResolver.resolve(resolvedHeavyModel);
        return new ResolvedHeavyModelRuntimeBinding(
                resolvedHeavyModel,
                heavyProviderAdapter,
                providerCredential);
    }
}
