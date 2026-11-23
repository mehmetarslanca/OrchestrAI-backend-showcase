package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DistributedChatStreamRegistry {

    public static final String CANCEL_CHANNEL = "chat:stream:cancel";

    private static final String REGISTRY_PREFIX = "chat:stream:";
    private static final String FIELD_NODE_ID = "nodeId";
    private static final String FIELD_USER_ID = "userId";
    private static final String FIELD_CANCEL_REQUESTED = "cancelRequested";
    private static final Duration STREAM_TTL = Duration.ofMinutes(10);

    private final StringRedisTemplate redisTemplate;

    public DistributedChatStreamRegistry(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private final String nodeId = UUID.randomUUID().toString();

    public void registerStream(final UUID chatId, final UUID userId) {
        final String key = streamKey(chatId);
        try {
            redisTemplate.opsForHash().putAll(key, Map.of(
                    FIELD_NODE_ID, nodeId,
                    FIELD_USER_ID, userId.toString(),
                    FIELD_CANCEL_REQUESTED, Boolean.FALSE.toString()));
            redisTemplate.expire(key, STREAM_TTL);
        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException ex) {
            throw new InfrastructureUnavailableException(
                    "Distributed chat stream registry is temporarily unavailable. Please retry shortly.",
                    ex);
        }
    }

    public void clearStream(final UUID chatId) {
        try {
            redisTemplate.delete(streamKey(chatId));
        } catch (Exception ex) {
            log.warn("Failed to clear distributed chat stream state for {}: {}", chatId, ex.getMessage());
        }
    }

    public boolean hasActiveStream(final UUID chatId) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(streamKey(chatId)));
        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException ex) {
            throw new InfrastructureUnavailableException(
                    "Distributed chat stream registry is temporarily unavailable. Please retry shortly.",
                    ex);
        }
    }

    public void requestCancel(final UUID chatId, final UUID userId) {
        final String key = streamKey(chatId);
        try {
            final Object ownerUserId = redisTemplate.opsForHash().get(key, FIELD_USER_ID);
            if (ownerUserId == null) {
                return;
            }

            if (!userId.toString().equals(ownerUserId.toString())) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Distributed chat stream ownership mismatch detected during cancellation.");
            }

            redisTemplate.opsForHash().put(key, FIELD_CANCEL_REQUESTED, Boolean.TRUE.toString());
            redisTemplate.expire(key, STREAM_TTL);
            redisTemplate.convertAndSend(CANCEL_CHANNEL, chatId.toString());
        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException ex) {
            throw new InfrastructureUnavailableException(
                    "Distributed chat stream registry is temporarily unavailable. Please retry shortly.",
                    ex);
        }
    }

    public boolean isCancelRequested(final UUID chatId) {
        try {
            final Object value = redisTemplate.opsForHash().get(streamKey(chatId), FIELD_CANCEL_REQUESTED);
            return Boolean.parseBoolean(String.valueOf(value));
        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException ex) {
            throw new InfrastructureUnavailableException(
                    "Distributed chat stream registry is temporarily unavailable. Please retry shortly.",
                    ex);
        }
    }

    public boolean isOwnedLocally(final UUID chatId) {
        try {
            final Object ownerNodeId = redisTemplate.opsForHash().get(streamKey(chatId), FIELD_NODE_ID);
            return nodeId.equals(ownerNodeId);
        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException ex) {
            throw new InfrastructureUnavailableException(
                    "Distributed chat stream registry is temporarily unavailable. Please retry shortly.",
                    ex);
        }
    }

    public UUID getOwnerUserId(final UUID chatId) {
        try {
            final Object ownerUserId = redisTemplate.opsForHash().get(streamKey(chatId), FIELD_USER_ID);
            return ownerUserId == null ? null : UUID.fromString(ownerUserId.toString());
        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException ex) {
            throw new InfrastructureUnavailableException(
                    "Distributed chat stream registry is temporarily unavailable. Please retry shortly.",
                    ex);
        }
    }

    public static UUID parseChatId(final byte[] messageBody) {
        return UUID.fromString(new String(messageBody, StandardCharsets.UTF_8));
    }

    private String streamKey(final UUID chatId) {
        return REGISTRY_PREFIX + chatId;
    }
}
