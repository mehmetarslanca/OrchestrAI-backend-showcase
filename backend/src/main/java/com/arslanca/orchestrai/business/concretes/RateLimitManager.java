package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.RateLimitService;
import com.arslanca.orchestrai.core.config.RateLimitProperties;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Redis-backed Token Bucket Rate Limiter.
 *
 * NOT: Virtual thread uyumlu — ThreadLocal kullanmaz.
 */
@Service
@Slf4j
public class RateLimitManager implements RateLimitService {

    private final RateLimitProperties rateLimitProperties;
    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> rateLimitScript;

    public RateLimitManager(
            final RateLimitProperties rateLimitProperties,
            final StringRedisTemplate redisTemplate,
            @Qualifier("rateLimitScript") final RedisScript<Long> rateLimitScript) {
        this.rateLimitProperties = rateLimitProperties;
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = rateLimitScript;
    }

    @Override
    public boolean tryAcquire(final UUID userId) {
        try {
            final String key = "rate_limit:" + userId.toString();
            final long now = Instant.now().toEpochMilli();
            
            final double refillRatePerMs = rateLimitProperties.requestsPerMinute() / 60_000.0;

            final Long result = redisTemplate.execute(
                rateLimitScript,
                List.of(key),
                String.valueOf(rateLimitProperties.burstCapacity()),
                String.valueOf(refillRatePerMs),
                String.valueOf(now)
            );

            final boolean allowed = result != null && result == 1L;

            if (!allowed) {
                log.warn("Rate limit aşıldı: userId={}", userId);
            }

            return allowed;

        } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException e) {
            log.warn("Redis unavailable for RateLimitManager: {}", e.getMessage());
            if (rateLimitProperties.failOpen()) {
                return true;
            }
            throw new InfrastructureUnavailableException(
                    "Rate limiting is temporarily unavailable. Please retry shortly.",
                    e);
        } catch (Exception e) {
            log.warn("Unexpected error in RateLimitManager: {}", e.getMessage());
            if (rateLimitProperties.failOpen()) {
                return true;
            }
            throw new InfrastructureUnavailableException(
                    "Rate limiting is temporarily unavailable. Please retry shortly.",
                    e);
        }
    }
}
