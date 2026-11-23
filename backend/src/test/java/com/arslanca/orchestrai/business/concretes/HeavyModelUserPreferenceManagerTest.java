package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.ai.HeavyModelPreference;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiConnectionRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserAiPreferenceRepository;
import com.arslanca.orchestrai.entity.concretes.UserAiConnectionEntity;
import com.arslanca.orchestrai.entity.concretes.UserAiPreferenceEntity;
import com.arslanca.orchestrai.entity.enums.AiConnectionType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HeavyModelUserPreferenceManagerTest {

    @Mock
    private UserAiPreferenceRepository userAiPreferenceRepository;

    @Mock
    private UserAiConnectionRepository userAiConnectionRepository;

    @Test
    void shouldPersistGenericSelectionAlongsideLegacyCompatibilityFields() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UserAiConnectionEntity connection = byokConnection(userId, connectionId, "openai");
        when(userAiPreferenceRepository.findById(userId)).thenReturn(Optional.empty());
        when(userAiConnectionRepository.findByIdAndUserId(connectionId, userId)).thenReturn(Optional.of(connection));
        when(userAiPreferenceRepository.save(any(UserAiPreferenceEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        HeavyModelUserPreferenceManager manager = new HeavyModelUserPreferenceManager(
                userAiPreferenceRepository,
                userAiConnectionRepository);

        HeavyModelPreference preference = manager.savePreference(userId, "openai:gpt-5.4", CredentialSource.BYOK, connectionId);

        ArgumentCaptor<UserAiPreferenceEntity> captor = ArgumentCaptor.forClass(UserAiPreferenceEntity.class);
        verify(userAiPreferenceRepository).save(captor.capture());
        UserAiPreferenceEntity savedEntity = captor.getValue();

        assertThat(preference.modelKey()).isEqualTo("openai:gpt-5.4");
        assertThat(preference.selectedConnectionId()).isEqualTo(connectionId);
        assertThat(savedEntity.getDefaultModelKey()).isEqualTo("openai:gpt-5.4");
        assertThat(savedEntity.getDefaultHeavyModelKey()).isEqualTo("openai:gpt-5.4");
        assertThat(savedEntity.getDefaultConnectionId()).isEqualTo(connectionId);
        assertThat(savedEntity.getDefaultAuthSource()).isEqualTo(CredentialSource.BYOK);
    }

    @Test
    void shouldReadGenericSelectionStateFromPreferenceEntity() {
        UUID userId = UUID.randomUUID();
        UUID connectionId = UUID.randomUUID();
        UserAiPreferenceEntity entity = UserAiPreferenceEntity.create(userId);
        entity.updateDefaultSelection("openai:gpt-5.4", connectionId, CredentialSource.BYOK);
        when(userAiPreferenceRepository.findById(userId)).thenReturn(Optional.of(entity));
        when(userAiConnectionRepository.findByIdAndUserId(connectionId, userId))
                .thenReturn(Optional.of(byokConnection(userId, connectionId, "openai")));

        HeavyModelUserPreferenceManager manager = new HeavyModelUserPreferenceManager(
                userAiPreferenceRepository,
                userAiConnectionRepository);

        Optional<HeavyModelPreference> preference = manager.findPreference(userId);

        assertThat(preference).isPresent();
        assertThat(preference.get().modelKey()).isEqualTo("openai:gpt-5.4");
        assertThat(preference.get().authSource()).isEqualTo(CredentialSource.BYOK);
        assertThat(preference.get().selectedConnectionId()).isEqualTo(connectionId);
    }

    private UserAiConnectionEntity byokConnection(final UUID userId, final UUID connectionId, final String providerKey) {
        UserAiConnectionEntity entity = UserAiConnectionEntity.createPendingByok(userId, AiConnectionType.OPENAI_BYOK, providerKey, 1);
        ReflectionTestUtils.setField(entity, "id", connectionId);
        return entity;
    }
}
