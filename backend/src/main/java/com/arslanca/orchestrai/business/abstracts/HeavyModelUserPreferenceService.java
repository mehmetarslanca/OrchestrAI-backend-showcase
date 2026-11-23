package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.ai.HeavyModelPreference;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;

import java.util.Optional;
import java.util.UUID;

public interface HeavyModelUserPreferenceService {

    default Optional<String> findDefaultHeavyModelKey(final UUID userId) {
        return findPreference(userId).map(HeavyModelPreference::modelKey);
    }

    Optional<HeavyModelPreference> findPreference(UUID userId);

    default HeavyModelPreference savePreference(
            final UUID userId,
            final String modelKey,
            final CredentialSource authSource) {
        return savePreference(userId, modelKey, authSource, null);
    }

    HeavyModelPreference savePreference(UUID userId, String modelKey, CredentialSource authSource, UUID selectedConnectionId);

    void clearPreference(UUID userId);
}
