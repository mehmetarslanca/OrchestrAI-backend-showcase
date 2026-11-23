package com.arslanca.orchestrai.business.concretes.cancellation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.arslanca.orchestrai.business.concretes.ProjectStateService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.core.exception.type.ProjectGenerationCancelledException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationSessionRegistryTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ProjectRepository projectRepository;

    private ProjectGenerationSessionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ProjectGenerationSessionRegistry(redisTemplate, projectRepository);
    }

    @Test
    void shouldRegisterAndRetrieveToken() {
        final UUID projectId = UUID.randomUUID();
        final GenerationCancellationToken token = registry.register(projectId);

        assertThat(token).isNotNull();
        assertThat(token.projectId()).isEqualTo(projectId);
        assertThat(registry.get(projectId)).isEqualTo(token);
    }

    @Test
    void shouldPublishCancellationSignalAndCancelLocalToken() {
        final UUID projectId = UUID.randomUUID();
        final GenerationCancellationToken token = registry.register(projectId);

        registry.markCancelled(projectId, GenerationFailureReason.USER_CANCELLED);

        assertThat(token.isCancellationRequested()).isTrue();
        assertThat(token.cancellationReason()).isEqualTo(GenerationFailureReason.USER_CANCELLED);
        verify(redisTemplate).convertAndSend(
                ProjectGenerationSessionRegistry.CANCEL_CHANNEL,
                projectId + ":" + GenerationFailureReason.USER_CANCELLED.metadataValue());
    }

    @Test
    void shouldDetectPersistedCancellationIntentThroughTokenProbe() {
        final UUID projectId = UUID.randomUUID();
        final GenerationCancellationToken token = registry.register(projectId);
        final ProjectEntity project = ProjectEntity.builder()
                .id(projectId)
                .build();
        project.appendMetadata(Map.of(
                ProjectStateService.CANCEL_REQUESTED_KEY, true,
                ProjectStateService.CANCEL_REQUESTED_REASON_KEY, GenerationFailureReason.CLIENT_TIMEOUT.metadataValue()));

        when(projectRepository.findById(projectId)).thenReturn(Optional.of(project));

        assertThatThrownBy(token::throwIfCancellationRequested)
                .isInstanceOf(ProjectGenerationCancelledException.class)
                .hasMessageContaining(GenerationFailureReason.CLIENT_TIMEOUT.metadataValue());
    }

    @Test
    void shouldParseDistributedSignal() {
        final UUID projectId = UUID.randomUUID();

        final ProjectGenerationSessionRegistry.CancellationSignal signal = ProjectGenerationSessionRegistry
                .parseCancelSignal((projectId + ":PING_FAILED").getBytes(java.nio.charset.StandardCharsets.UTF_8));

        assertThat(signal.projectId()).isEqualTo(projectId);
        assertThat(signal.reason()).isEqualTo(GenerationFailureReason.PING_FAILED);
    }
}
