package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.HeavyProviderAdapter;

import java.util.UUID;

public interface HeavyModelResolutionService {

    default ResolvedHeavyModel resolve(final String requestedHeavyModelKey) {
        return resolve(null, requestedHeavyModelKey, null);
    }

    default ResolvedHeavyModel resolve(final UUID userId, final String requestedHeavyModelKey) {
        return resolve(userId, requestedHeavyModelKey, null);
    }

    ResolvedHeavyModel resolve(UUID userId, String requestedHeavyModelKey, CredentialSource requestedAuthSource);

    HeavyProviderAdapter adapterFor(ResolvedHeavyModel resolvedHeavyModel);
}
