package com.arslanca.orchestrai.business.concretes.cancellation;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.concretes.ProjectStateService;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;

@Component
public class ProjectGenerationSessionRegistry {

    public static final String CANCEL_CHANNEL = "project:generation:cancel";
    private static final String MESSAGE_SEPARATOR = ":";
    private static final Logger log = LoggerFactory.getLogger(ProjectGenerationSessionRegistry.class);

    private final ConcurrentHashMap<UUID, GenerationCancellationToken> sessions = new ConcurrentHashMap<>();
    private final StringRedisTemplate redisTemplate;
    private final ProjectRepository projectRepository;

    public ProjectGenerationSessionRegistry(
            final StringRedisTemplate redisTemplate,
            final ProjectRepository projectRepository) {
        this.redisTemplate = redisTemplate;
        this.projectRepository = projectRepository;
    }

    public GenerationCancellationToken register(final UUID projectId) {
        final GenerationCancellationToken token = new GenerationCancellationToken(projectId,
                () -> loadPersistedCancellationReason(projectId));
        sessions.put(projectId, token);
        return token;
    }

    public GenerationCancellationToken get(final UUID projectId) {
        return sessions.get(projectId);
    }

    public void markCancelled(final UUID projectId, final GenerationFailureReason reason) {
        applyCancellationSignal(projectId, reason);
        publishCancellation(projectId, reason);
    }

    public void applyCancellationSignal(final UUID projectId, final GenerationFailureReason reason) {
        final GenerationCancellationToken token = sessions.get(projectId);
        if (token != null) {
            token.cancel(reason);
        }
    }

    public void unregister(final UUID projectId) {
        sessions.remove(projectId);
    }

    public static CancellationSignal parseCancelSignal(final byte[] messageBody) {
        final String payload = new String(messageBody, StandardCharsets.UTF_8);
        final int separatorIndex = payload.indexOf(MESSAGE_SEPARATOR);
        if (separatorIndex <= 0 || separatorIndex >= payload.length() - 1) {
            throw new IllegalArgumentException("Invalid project generation cancel payload: " + payload);
        }

        return new CancellationSignal(
                UUID.fromString(payload.substring(0, separatorIndex)),
                GenerationFailureReason.fromMetadataValue(payload.substring(separatorIndex + 1)));
    }

    private GenerationFailureReason loadPersistedCancellationReason(final UUID projectId) {
        try {
            final java.util.Optional<ProjectEntity> projectOptional = projectRepository.findById(projectId);
            if (projectOptional.isEmpty()) {
                return null;
            }

            final ProjectEntity project = projectOptional.get();
            if (!project.isActive()) {
                return null;
            }

            return extractPersistedCancellationReason(project.getMetadata());
        } catch (final Exception exception) {
            log.debug("Failed to probe persisted cancellation state for project {}: {}", projectId,
                    exception.getMessage());
            return null;
        }
    }

    private GenerationFailureReason extractPersistedCancellationReason(final Map<String, Object> metadata) {
        if (metadata == null) {
            return null;
        }

        final Object cancelRequested = metadata.get(ProjectStateService.CANCEL_REQUESTED_KEY);
        if (!Boolean.parseBoolean(String.valueOf(cancelRequested))) {
            return null;
        }

        final Object reason = metadata.get(ProjectStateService.CANCEL_REQUESTED_REASON_KEY);
        return reason == null ? null : GenerationFailureReason.fromMetadataValue(String.valueOf(reason));
    }

    private void publishCancellation(final UUID projectId, final GenerationFailureReason reason) {
        try {
            redisTemplate.convertAndSend(CANCEL_CHANNEL, projectId + MESSAGE_SEPARATOR + reason.metadataValue());
        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException exception) {
            log.warn("Failed to publish project cancellation signal for {}: {}", projectId, exception.getMessage());
        }
    }

    public record CancellationSignal(UUID projectId, GenerationFailureReason reason) {
    }
}
