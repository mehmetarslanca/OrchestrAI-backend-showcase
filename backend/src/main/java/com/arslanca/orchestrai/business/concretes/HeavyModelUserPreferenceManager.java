package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.HeavyModelUserPreferenceService;
import com.arslanca.orchestrai.business.dtos.ai.HeavyModelPreference;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.core.ai.provider.HeavyModelKeyNormalizer;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiPreferenceRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.concretes.UserAiPreferenceEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
public class HeavyModelUserPreferenceManager implements HeavyModelUserPreferenceService {

    private final UserAiPreferenceRepository userAiPreferenceRepository;
    private final UserAiConnectionRepository userAiConnectionRepository;

    public HeavyModelUserPreferenceManager(
            final UserAiPreferenceRepository userAiPreferenceRepository,
            final UserAiConnectionRepository userAiConnectionRepository) {
        this.userAiPreferenceRepository = userAiPreferenceRepository;
        this.userAiConnectionRepository = userAiConnectionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<HeavyModelPreference> findPreference(final UUID userId) {
        return userAiPreferenceRepository.findById(userId)
                .flatMap(this::toPreference);
    }

    @Override
    @Transactional
    public HeavyModelPreference savePreference(
            final UUID userId,
            final String modelKey,
            final CredentialSource authSource,
            final UUID selectedConnectionId) {
        if (authSource == null) {
            throw new IllegalArgumentException("defaultAuthSource belirtilmelidir.");
        }

        final String normalizedModelKey = HeavyModelKeyNormalizer.normalize(modelKey);
        final UUID resolvedConnectionId = resolveSelectedConnectionId(userId, normalizedModelKey, authSource, selectedConnectionId);
        final UserAiPreferenceEntity entity = userAiPreferenceRepository.findById(userId)
                .orElseGet(() -> UserAiPreferenceEntity.create(userId));
        entity.updateDefaultSelection(normalizedModelKey, resolvedConnectionId, authSource);
        userAiPreferenceRepository.save(entity);
        return new HeavyModelPreference(normalizedModelKey, authSource, resolvedConnectionId);
    }

    @Override
    @Transactional
    public void clearPreference(final UUID userId) {
        userAiPreferenceRepository.deleteById(userId);
    }

    private Optional<HeavyModelPreference> toPreference(final UserAiPreferenceEntity entity) {
        final String selectedModelKey = entity.resolveDefaultModelKey();
        if (selectedModelKey == null || selectedModelKey.isBlank()) {
            return Optional.empty();
        }

        final UUID selectedConnectionId = entity.getDefaultConnectionId();
        final CredentialSource authSource = resolveAuthSource(entity, selectedConnectionId);
        if (authSource == null) {
            return Optional.empty();
        }
        return Optional.of(new HeavyModelPreference(selectedModelKey, authSource, selectedConnectionId));
    }

    private CredentialSource resolveAuthSource(
            final UserAiPreferenceEntity entity,
            final UUID selectedConnectionId) {
        if (selectedConnectionId == null) {
            return entity.getDefaultAuthSource();
        }
        return userAiConnectionRepository.findByIdAndUserId(selectedConnectionId, entity.getUserId())
                .map(this::toCredentialSource)
                .orElse(entity.getDefaultAuthSource());
    }

    private UUID resolveSelectedConnectionId(
            final UUID userId,
            final String normalizedModelKey,
            final CredentialSource authSource,
            final UUID selectedConnectionId) {
        if (authSource == CredentialSource.PLATFORM) {
            if (selectedConnectionId != null) {
                throw new IllegalArgumentException("Platform modeli icin defaultConnectionId gonderilemez.");
            }
            return null;
        }

        if (selectedConnectionId != null) {
            return validateSelectedConnection(userId, normalizedModelKey, authSource, selectedConnectionId).getId();
        }

        final Optional<UUID> derivedConnectionId = deriveSelectedConnectionId(userId, normalizedModelKey, authSource);
        if (authSource == CredentialSource.USER_LINKED && derivedConnectionId.isEmpty()) {
            throw new IllegalArgumentException("USER_LINKED model secimi icin defaultConnectionId belirtilmelidir.");
        }
        return derivedConnectionId.orElse(null);
    }

    private UserAiConnectionEntity validateSelectedConnection(
            final UUID userId,
            final String normalizedModelKey,
            final CredentialSource authSource,
            final UUID selectedConnectionId) {
        final UserAiConnectionEntity connection = userAiConnectionRepository.findByIdAndUserId(selectedConnectionId, userId)
                .orElseThrow(() -> new IllegalArgumentException("Secilen AI connection bulunamadi."));
        if (toCredentialSource(connection) != authSource) {
            throw new IllegalArgumentException("Secilen AI connection auth source ile uyusmuyor.");
        }
        if (!providerKeyOf(normalizedModelKey).equals(connection.getProviderKey())) {
            throw new IllegalArgumentException("Secilen AI connection model provider'i ile uyusmuyor.");
        }
        return connection;
    }

    private Optional<UUID> deriveSelectedConnectionId(
            final UUID userId,
            final String normalizedModelKey,
            final CredentialSource authSource) {
        final String providerKey = providerKeyOf(normalizedModelKey);
        return userAiConnectionRepository.findAllByUserId(userId).stream()
                .filter(connection -> providerKey.equals(connection.getProviderKey()))
                .filter(connection -> toCredentialSource(connection) == authSource)
                .map(UserAiConnectionEntity::getId)
                .filter(connectionId -> connectionId != null)
                .findFirst();
    }

    private CredentialSource toCredentialSource(final UserAiConnectionEntity connection) {
        return switch (connection.getBillingMode()) {
            case PLATFORM_PAID -> CredentialSource.PLATFORM;
            case BYOK -> CredentialSource.BYOK;
            case USER_LINKED -> CredentialSource.USER_LINKED;
        };
    }

    private String providerKeyOf(final String normalizedModelKey) {
        final int separatorIndex = normalizedModelKey.indexOf(':');
        return separatorIndex < 0 ? normalizedModelKey : normalizedModelKey.substring(0, separatorIndex);
    }
}
