package com.arslanca.orchestrai.integration.redis;

import com.arslanca.orchestrai.core.idempotency.IdempotencyStore;
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

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class IdempotencyStoreIntegrationTest {

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
    }

    @Autowired
    private IdempotencyStore idempotencyStore;

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
    @DisplayName("GIVEN 10 concurrent requests with exact same idempotency key WHEN checkAndMark THEN exactly 1 succeeds")
    void shouldEnforceIdempotencyUnderLoad() throws InterruptedException {
        // Arrange
        String idempotencyKey = UUID.randomUUID().toString();
        int totalRequests = 10;
        int expectedSuccess = 1;

        ExecutorService executorService = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(totalRequests);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger duplicateCount = new AtomicInteger(0);

        // Act
        for (int i = 0; i < totalRequests; i++) {
            executorService.submit(() -> {
                try {
                    startLatch.await();
                    boolean isNew = idempotencyStore.checkAndMark(idempotencyKey);
                    if (isNew) {
                        successCount.incrementAndGet();
                    } else {
                        duplicateCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Releasing the barrier
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        assertThat(completed).isTrue();
        executorService.shutdown();

        // Assert
        assertThat(successCount.get()).isEqualTo(expectedSuccess);
        assertThat(duplicateCount.get()).isEqualTo(totalRequests - expectedSuccess);

        // Verify key exists and value is "1" in Redis
        String key = "idempotency:" + idempotencyKey;
        String val = redisTemplate.opsForValue().get(key);
        assertThat(val).isEqualTo("1");
    }
}
