package com.arslanca.orchestrai.core.job;

import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.IdeTarget;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
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

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class SemaphoreReconciliationJobTest {

    @Container
    static RedisContainer redis = new RedisContainer(RedisContainer.DEFAULT_IMAGE_NAME.withTag("7.0-alpine"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("orchestrai")
            .withUsername("testuser")
            .withPassword("testpass");

    @DynamicPropertySource
    static void configureProperties(final DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", redis::getFirstMappedPort);
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SemaphoreReconciliationJob job;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private UserEntity testUser;

    @BeforeEach
    void setUp() {
        testUser = userRepository.save(UserEntity.builder()
                .githubId(99999L)
                .email("job_test@orchestrai.com")
                .username("job_test")
                .build());
    }

    @AfterEach
    void tearDown() {
        projectRepository.deleteAll();
        userRepository.deleteAll();
        if (redisTemplate.getConnectionFactory() != null) {
            redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
        }
    }

    @Test
    @DisplayName("reconcileSemaphore syncs Redis leases with PROCESSING project ids")
    void shouldSynchronizeRedisLeasesWithProcessingProjects() {
        ProjectEntity processingProject = ProjectEntity.builder()
                .projectName("reconcile-app")
                .user(testUser)
                .ideTarget(IdeTarget.CURSOR)
                .originalPrompt("build something")
                .build();
        processingProject = projectRepository.saveAndFlush(processingProject);
        processingProject.markAsProcessing();
        processingProject = projectRepository.saveAndFlush(processingProject);

        redisTemplate.opsForSet().add("semaphore:active_count", "stale-lease");

        job.reconcileSemaphore();

        final Set<String> members = redisTemplate.opsForSet().members("semaphore:active_count");
        assertThat(members)
                .isNotNull()
                .containsExactly(processingProject.getId().toString());
        assertThat(projectRepository.findByStatus(ProjectStatus.PROCESSING)).hasSize(1);
    }
}
