package com.arslanca.orchestrai.integration.redis;

import com.arslanca.orchestrai.business.abstracts.DistributedSemaphoreService;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DistributedSemaphoreManagerIntegrationTest {

  @Container
  static RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("7.0-alpine"));

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
      .withDatabaseName("orchestrai")
      .withUsername("testuser")
      .withPassword("testpass");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.data.redis.host", redis::getHost);
    registry.add("spring.data.redis.port", redis::getFirstMappedPort);
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("orchestrai.pipeline.max-concurrent", () -> "50");
  }

  @Autowired
  private DistributedSemaphoreService semaphoreService;

  @Autowired
  private StringRedisTemplate redisTemplate;

  @BeforeEach
  @AfterEach
  void clearRedis() {
    if (redisTemplate.getConnectionFactory() != null) {
      redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
  }

  @Test
  @DisplayName("GIVEN 55 concurrent threads WHEN tryAcquire max 50 THEN 50 succeed and 5 fail")
  void shouldEnforceMaxConcurrentSemaphoreUnderLoad() throws InterruptedException {
    // Arrange
    int totalThreads = 55;
    int expectedMaxConcurrent = 50;
    String resourceName = "test_active_count";

    ExecutorService executorService = Executors.newFixedThreadPool(totalThreads);
    CountDownLatch startLatch = new CountDownLatch(1);
    CountDownLatch doneLatch = new CountDownLatch(totalThreads);

    AtomicInteger successCount = new AtomicInteger(0);
    AtomicInteger failCount = new AtomicInteger(0);

    // Act
    for (int i = 0; i < totalThreads; i++) {
      executorService.submit(() -> {
        try {
          startLatch.await(); // Wait until all threads are ready
          boolean acquired = semaphoreService.tryAcquire(resourceName, java.util.UUID.randomUUID().toString());
          if (acquired) {
            successCount.incrementAndGet();
          } else {
            failCount.incrementAndGet();
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        } finally {
          doneLatch.countDown();
        }
      });
    }

    // Unleash all threads simultaneously
    startLatch.countDown();

    // Wait for all threads to finish
    boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
    assertThat(completed).isTrue();
    executorService.shutdown();

    // Assert
    assertThat(successCount.get()).isEqualTo(expectedMaxConcurrent);
    assertThat(failCount.get()).isEqualTo(totalThreads - expectedMaxConcurrent);

    // Verify final Redis state
    String key = "semaphore:" + resourceName;
    java.util.Set<String> activeLeases = redisTemplate.opsForZSet().range(key, 0, -1);
    assertThat(activeLeases).isNotNull();
    assertThat(activeLeases).hasSize(expectedMaxConcurrent);
  }
}
