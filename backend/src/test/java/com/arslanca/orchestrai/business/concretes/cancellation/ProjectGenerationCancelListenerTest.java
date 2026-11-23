package com.arslanca.orchestrai.business.concretes.cancellation;

import static org.mockito.Mockito.verify;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.connection.DefaultMessage;

import com.arslanca.orchestrai.business.enums.GenerationFailureReason;

@ExtendWith(MockitoExtension.class)
class ProjectGenerationCancelListenerTest {

    @Mock
    private ProjectGenerationSessionRegistry sessionRegistry;

    @Test
    void shouldApplyIncomingDistributedCancellationSignal() {
        final ProjectGenerationCancelListener listener = new ProjectGenerationCancelListener(sessionRegistry);
        final UUID projectId = UUID.randomUUID();

        listener.onMessage(
                new DefaultMessage(
                        ProjectGenerationSessionRegistry.CANCEL_CHANNEL.getBytes(StandardCharsets.UTF_8),
                        (projectId + ":CLIENT_DISCONNECTED").getBytes(StandardCharsets.UTF_8)),
                null);

        verify(sessionRegistry).applyCancellationSignal(projectId, GenerationFailureReason.CLIENT_DISCONNECTED);
    }
}
