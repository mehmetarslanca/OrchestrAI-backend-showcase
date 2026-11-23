package com.arslanca.orchestrai.business.concretes.cancellation;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.concretes.ProjectStateService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationCancellationManagerTest {

    @Mock
    private ProjectGenerationSessionRegistry registry;

    @Mock
    private ProjectStateService projectStateService;

    private ProjectGenerationCancellationManager manager;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        manager = new ProjectGenerationCancellationManager(registry, projectStateService, meterRegistry);
    }

    @Test
    void shouldOpenSession() {
        final UUID projectId = UUID.randomUUID();
        manager.openSession(projectId);

        verify(registry).register(projectId);
    }

    @Test
    void shouldCancelGenerationWhenProjectIsStillActive() {
        final UUID projectId = UUID.randomUUID();
        when(projectStateService.markCancellationRequested(projectId, GenerationFailureReason.USER_CANCELLED))
                .thenReturn(true);

        manager.cancelGeneration(projectId, GenerationFailureReason.USER_CANCELLED);

        verify(registry).markCancelled(projectId, GenerationFailureReason.USER_CANCELLED);
        org.assertj.core.api.Assertions.assertThat(meterRegistry.counter(
                "project_generation.cancel.requested",
                "reason", "user_cancelled").count()).isEqualTo(1.0d);
    }

    @Test
    void shouldIgnoreCancelGenerationWhenProjectIsAlreadyTerminal() {
        final UUID projectId = UUID.randomUUID();
        when(projectStateService.markCancellationRequested(projectId, GenerationFailureReason.CLIENT_TIMEOUT))
                .thenReturn(false);

        manager.cancelGeneration(projectId, GenerationFailureReason.CLIENT_TIMEOUT);

        verify(registry, never()).markCancelled(projectId, GenerationFailureReason.CLIENT_TIMEOUT);
    }

    @Test
    void shouldCloseSession() {
        final UUID projectId = UUID.randomUUID();

        manager.closeSession(projectId);

        verify(registry).unregister(projectId);
    }
}
