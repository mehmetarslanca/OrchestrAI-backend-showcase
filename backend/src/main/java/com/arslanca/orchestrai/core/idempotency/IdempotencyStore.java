package com.arslanca.orchestrai.core.idempotency;

import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis-backed idempotency key store.
 */
@Component
@Slf4j
public class IdempotencyStore {

    private static final long TTL_MINUTES = 5L;

    private final StringRedisTemplate redisTemplate;

    public IdempotencyStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    /**
     * Verilen key'i kontrol eder ve işaretler.
     *
     * @param idempotencyKey CLI tarafından üretilen benzersiz anahtar
     * @return {@code true} eğer istek yeni ve işlenebilir, {@code false} eğer duplicate
     */
    public boolean checkAndMark(final String idempotencyKey) {
        try {
            final String key = "idempotency:" + idempotencyKey;
            
            // Atomik kontrol ve kilitleme (SET key value NX EX TTL)
            final Boolean isNew = redisTemplate.opsForValue().setIfAbsent(key, "1", Duration.ofMinutes(TTL_MINUTES));

            if (Boolean.TRUE.equals(isNew)) {
                return true;
            }

            log.warn("Duplicate request detected for idempotency key: {}", idempotencyKey);
            return false;

        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException e) {
            log.warn("Redis unavailable for IdempotencyStore: {}", e.getMessage());
            throw new InfrastructureUnavailableException(
                    "Idempotency protection is temporarily unavailable. Please retry once Redis connectivity is restored.",
                    e);
        } catch (Exception e) {
            log.warn("Unexpected error in IdempotencyStore: {}", e.getMessage());
            throw new InfrastructureUnavailableException(
                    "Idempotency protection is temporarily unavailable. Please retry shortly.",
                    e);
        }
    }
}
