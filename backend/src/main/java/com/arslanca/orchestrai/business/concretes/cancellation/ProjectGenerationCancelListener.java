package com.arslanca.orchestrai.business.concretes.cancellation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@Component
public class ProjectGenerationCancelListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(ProjectGenerationCancelListener.class);

    private final ProjectGenerationSessionRegistry sessionRegistry;

    public ProjectGenerationCancelListener(final ProjectGenerationSessionRegistry sessionRegistry) {
        this.sessionRegistry = sessionRegistry;
    }

    @Override
    public void onMessage(final Message message, final byte[] pattern) {
        try {
            final ProjectGenerationSessionRegistry.CancellationSignal signal = ProjectGenerationSessionRegistry
                    .parseCancelSignal(message.getBody());
            sessionRegistry.applyCancellationSignal(signal.projectId(), signal.reason());
        } catch (final Exception exception) {
            log.error("Failed to process project generation cancellation signal: {}", exception.getMessage());
        }
    }
}
