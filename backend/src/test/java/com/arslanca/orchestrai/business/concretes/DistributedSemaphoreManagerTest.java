package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.core.config.PipelineProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DistributedSemaphoreManagerTest {

  @Mock
  private StringRedisTemplate redisTemplate;

  @Mock
  private PipelineProperties pipelineProperties;

  @Mock
  private RedisScript<Long> semaphoreAcquireScript;

  @Test
  void shouldAcquireLeaseWhenScriptAllows() {
    when(pipelineProperties.maxConcurrent()).thenReturn(10);
    when(redisTemplate.execute(
        eq(semaphoreAcquireScript),
        eq(List.of("semaphore:test_resource")),
        eq("lease-1"),
        eq("10"),
        anyString(),
        eq("600000")))
        .thenReturn(1L);

    final DistributedSemaphoreManager manager = new DistributedSemaphoreManager(
        redisTemplate,
        pipelineProperties,
        semaphoreAcquireScript);

    assertThat(manager.tryAcquire("test_resource", "lease-1")).isTrue();
  }

  @Test
  void shouldRejectLeaseWhenLimitReached() {
    when(pipelineProperties.maxConcurrent()).thenReturn(10);
    when(redisTemplate.execute(
        eq(semaphoreAcquireScript),
        eq(List.of("semaphore:test_resource")),
        eq("lease-1"),
        eq("10"),
        anyString(),
        eq("600000")))
        .thenReturn(0L);

    final DistributedSemaphoreManager manager = new DistributedSemaphoreManager(
        redisTemplate,
        pipelineProperties,
        semaphoreAcquireScript);

    assertThat(manager.tryAcquire("test_resource", "lease-1")).isFalse();
  }

  @Test
  void shouldTreatDuplicateReleaseAsNoOp() {
    @SuppressWarnings("unchecked")
    final ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

    final DistributedSemaphoreManager manager = new DistributedSemaphoreManager(
        redisTemplate,
        pipelineProperties,
        semaphoreAcquireScript);

    manager.release("test_resource", "lease-1");

    verify(zSetOperations).remove("semaphore:test_resource", "lease-1");
  }

  @Test
  void shouldFallbackToLocalWhenRedisIsUnavailableDuringAcquire() {
    when(pipelineProperties.maxConcurrent()).thenReturn(10);
    when(redisTemplate.execute(eq(semaphoreAcquireScript), anyList(), eq("lease-1"), eq("10"), anyString(),
        eq("600000")))
        .thenThrow(new RedisConnectionFailureException("Redis down"));

    final DistributedSemaphoreManager manager = new DistributedSemaphoreManager(
        redisTemplate,
        pipelineProperties,
        semaphoreAcquireScript);

    boolean result = manager.tryAcquire("test_resource", "lease-1");
    assertThat(result).isTrue();
  }

  @Test
  void shouldReturnActiveLeasesFromRedisSet() {
    @SuppressWarnings("unchecked")
    final ZSetOperations<String, String> zSetOperations = mock(ZSetOperations.class);
    when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
    when(zSetOperations.range("semaphore:test_resource", 0, -1)).thenReturn(Set.of("lease-1", "lease-2"));

    final DistributedSemaphoreManager manager = new DistributedSemaphoreManager(
        redisTemplate,
        pipelineProperties,
        semaphoreAcquireScript);

    assertThat(manager.getActiveLeases("test_resource")).containsExactlyInAnyOrder("lease-1", "lease-2");
  }
}
