package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.DistributedSemaphoreService;
import com.arslanca.orchestrai.core.config.PipelineProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

@Service
@Slf4j
public class DistributedSemaphoreManager implements DistributedSemaphoreService {

  static final String SEMAPHORE_KEY_PREFIX = "semaphore:";

  private final StringRedisTemplate redisTemplate;
  private final PipelineProperties pipelineProperties;
  private final RedisScript<Long> semaphoreAcquireScript;
  
  // Local fallback state
  private final ConcurrentHashMap<String, Set<String>> localLeases = new ConcurrentHashMap<>();

  public DistributedSemaphoreManager(
      final StringRedisTemplate redisTemplate,
      final PipelineProperties pipelineProperties,
      @Qualifier("semaphoreAcquireScript") final RedisScript<Long> semaphoreAcquireScript) {
    this.redisTemplate = redisTemplate;
    this.pipelineProperties = pipelineProperties;
    this.semaphoreAcquireScript = semaphoreAcquireScript;
  }

  @Override
  public boolean tryAcquire(final String resource, final String leaseToken) {
    final String key = semaphoreKey(resource);
    try {
      final long currentTime = System.currentTimeMillis();
      final long ttlMs = 10 * 60 * 1000; // 10 minutes TTL

      final Long result = redisTemplate.execute(
          semaphoreAcquireScript,
          List.of(key),
          leaseToken,
          String.valueOf(pipelineProperties.maxConcurrent()),
          String.valueOf(currentTime),
          String.valueOf(ttlMs));

      if (result == null) {
        log.warn("Redis returned null, falling back to local semaphore for {}", resource);
        return fallbackTryAcquire(resource, leaseToken);
      }

      return result == 1L;
    } catch (RedisConnectionFailureException | org.springframework.dao.QueryTimeoutException e) {
      log.warn("Redis unavailable for semaphore {}: {}. Falling back to local tracking.", resource, e.getMessage());
      return fallbackTryAcquire(resource, leaseToken);
    } catch (Exception e) {
      log.warn("Unexpected error in semaphore {}: {}. Falling back to local tracking.", resource, e.getMessage());
      return fallbackTryAcquire(resource, leaseToken);
    }
  }

  private boolean fallbackTryAcquire(String resource, String leaseToken) {
    localLeases.putIfAbsent(resource, new CopyOnWriteArraySet<>());
    Set<String> leases = localLeases.get(resource);
    if (leases.size() < pipelineProperties.maxConcurrent()) {
        leases.add(leaseToken);
        return true;
    }
    return false;
  }

  @Override
  public void release(final String resource, final String leaseToken) {
    final String key = semaphoreKey(resource);
    try {
      redisTemplate.opsForZSet().remove(key, leaseToken);
    } catch (Exception e) {
      log.warn("Failed to release Redis semaphore for resource {}: {}", resource, e.getMessage());
    } finally {
      // Always cleanup local just in case
      Set<String> leases = localLeases.get(resource);
      if (leases != null) {
          leases.remove(leaseToken);
      }
    }
  }

  @Override
  public Set<String> getActiveLeases(final String resource) {
      final String key = semaphoreKey(resource);
      try {
          Set<String> members = redisTemplate.opsForZSet().range(key, 0, -1);
          return members != null ? members : Collections.emptySet();
      } catch (Exception e) {
          log.warn("Could not get active leases from Redis for {}: {}. Returning local leases.", resource, e.getMessage());
          Set<String> leases = localLeases.get(resource);
          return leases != null ? Set.copyOf(leases) : Collections.emptySet();
      }
  }

  public static String semaphoreKey(final String resource) {
    return SEMAPHORE_KEY_PREFIX + resource;
  }
}
