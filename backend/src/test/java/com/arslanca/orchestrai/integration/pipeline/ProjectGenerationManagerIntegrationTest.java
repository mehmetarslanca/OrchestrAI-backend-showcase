package com.arslanca.orchestrai.integration.pipeline;

import com.arslanca.orchestrai.business.abstracts.delivery.SseEventPublisher;
import com.arslanca.orchestrai.business.abstracts.ScaffoldMaterializationService;
import com.arslanca.orchestrai.business.abstracts.StarterResolutionService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.OrchestrationService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.concretes.HeavyModelCredentialResolver;
import com.arslanca.orchestrai.business.concretes.ProjectGenerationManager;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.project.ProjectRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializationMetadata;
import com.arslanca.orchestrai.business.dtos.projectgeneration.OrchestrationResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;
import com.arslanca.orchestrai.dataaccess.abstracts.CreditRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatPlanningSnapshotRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.UserRepository;
import com.arslanca.orchestrai.entity.concretes.CreditEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectEntity;
import com.arslanca.orchestrai.entity.concretes.UserEntity;
import com.arslanca.orchestrai.entity.enums.ProjectStatus;
import com.arslanca.orchestrai.business.abstracts.BlueprintGeneratorService;
import com.arslanca.orchestrai.business.abstracts.PromptAnalyzerService;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.planning.blueprint.BlueprintPlanCompatibilityAdapter;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class ProjectGenerationManagerIntegrationTest {

  private static final BlueprintPlanCompatibilityAdapter BLUEPRINT_PLAN_COMPATIBILITY_ADAPTER = new BlueprintPlanCompatibilityAdapter();

  @TestConfiguration
  static class DelayedExecutorConfig {
    @Bean
    @org.springframework.context.annotation.Primary
    public TaskExecutor taskExecutor() {
      return new SimpleAsyncTaskExecutor() {
        @Override
        public void execute(Runnable task) {
          super.execute(() -> {
            try {
              Thread.sleep(500); // Workaround: Allow main transaction to commit first
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            }
            task.run();
          });
        }
      };
    }
  }

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

  @MockitoBean
  private PromptAnalyzerService promptAnalyzerService;

  @MockitoBean
  private BlueprintGeneratorService blueprintGeneratorService;

  @MockitoBean
  private HeavyModelResolutionService heavyModelResolutionService;

  @MockitoBean
  private HeavyModelCredentialResolver heavyModelCredentialResolver;

  @MockitoBean
  private PlanningModelPolicyService planningModelPolicyService;

  @MockitoBean
  private OrchestrationService orchestrationService;

  @MockitoBean
  private StarterResolutionService starterResolutionService;

  @MockitoBean
  private ScaffoldMaterializationService scaffoldMaterializationService;

  @Autowired
  private ProjectGenerationManager projectGenerationManager;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private CreditRepository creditRepository;

  @Autowired
  private ProjectRepository projectRepository;

  @Autowired
  private ChatPlanningSnapshotRepository chatPlanningSnapshotRepository;

  @Autowired
  private ProjectGenerationPreparationRepository preparationRepository;

  @Autowired
  private ProjectPlanningPreviewRepository planningPreviewRepository;

  @Autowired
  private StringRedisTemplate redisTemplate;

  private UserEntity testUser;

  @BeforeEach
  void setUp() {
    // Prepare database with user and credits
    testUser = UserEntity.builder()
        .githubId(12345L)
        .email("test@orchestrai.com")
        .username("test_user")
        .build();
    userRepository.save(testUser);

    CreditEntity credit = CreditEntity.builder()
        .user(testUser)
        .balance(10)
        .build();
    creditRepository.save(credit);
  }

  @AfterEach
  void tearDown() {
    chatPlanningSnapshotRepository.deleteAll();
    planningPreviewRepository.deleteAll();
    preparationRepository.deleteAll();
    projectRepository.deleteAll();
    creditRepository.deleteAll();
    userRepository.deleteAll();
    if (redisTemplate.getConnectionFactory() != null) {
      redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }
  }

  @Test
  @DisplayName("GIVEN invalid preparation analysis WHEN generation is requested THEN preparation fails before project creation")
  void shouldFailFastWhenPreparationAnalysisFails() {
    // Arrange
    when(promptAnalyzerService.analyzePrompt(any())).thenThrow(new RuntimeException("LLM Timeout simulation"));
    when(heavyModelResolutionService.resolve(
        org.mockito.ArgumentMatchers.any(UUID.class),
        org.mockito.ArgumentMatchers.nullable(String.class),
        org.mockito.ArgumentMatchers.nullable(com.arslanca.orchestrai.core.ai.provider.CredentialSource.class)))
        .thenReturn(resolvedHeavyModel());

    ProjectRequest request = new ProjectRequest("timeout-test", "cursor", "Make a simple app", null);
    SseEventPublisher publisher = mock(SseEventPublisher.class);

    org.assertj.core.api.Assertions.assertThatThrownBy(
        () -> projectGenerationManager.generateBlueprint(testUser.getId(), request, publisher))
        .isInstanceOf(RuntimeException.class)
        .hasMessageContaining("LLM Timeout simulation");

    // Assert
    assertThat(projectRepository.findAll()).isEmpty();
    assertThat(preparationRepository.findAll()).isEmpty();

    CreditEntity credit = creditRepository.findByUserId(testUser.getId()).orElseThrow();
    assertThat(credit.getBalance()).isEqualTo(10);
    assertThat(credit.getReservedCredits()).isEqualTo(0);
  }

  @Test
  @DisplayName("GIVEN valid prompt WHEN LLM responds successfully THEN project marked COMPLETED and credits deducted")
  void shouldCompleteSuccessfullyWhenLlmWorks() {
    // Arrange
    StructuredAnalysis mockAnalysis = new StructuredAnalysis(
        "success-test",
        "saas",
        java.util.List.of("nextjs", "postgres"),
        java.util.List.of("auth", "billing"),
        "Low",
        false,
        null,
        "Summary",
        java.util.List.of("admin"),
        java.util.List.of("Launch a small SaaS"),
        java.util.List.of("sign up"),
        java.util.List.of("web"),
        true,
        true,
        false,
        "cloud",
        "single-tenant",
        java.util.List.of("postgres"),
        java.util.List.of(),
        java.util.List.of("rbac"),
        java.util.List.of(),
        "steady growth",
        "interactive",
        java.util.List.of(),
        java.util.List.of(),
        java.util.List.of(),
        java.util.List.of(),
        java.util.List.of(),
        java.util.List.of(),
        java.util.List.of(),
        StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    BlueprintPlan.PhaseDefinition foundationPhase = new BlueprintPlan.PhaseDefinition(
        1,
        "Foundation",
        "Establish the backend and frontend execution path for the starter-backed SaaS shell.",
        "Needed to turn the approved starter into a delivery-ready plan.",
        java.util.List.of("auth", "dashboard"),
        java.util.List.of(),
        java.util.List.of("starter approved"),
        java.util.List.of("starter wiring", "auth flow", "dashboard shell"),
        java.util.List.of("reuse approved starter modules"),
        java.util.List.of("starter contract"),
        "initial tenant and account persistence",
        java.util.List.of("protect auth endpoints"),
        "basic request and error telemetry",
        "unit tests plus starter smoke coverage",
        java.util.List.of("starter boots", "auth path works", "dashboard renders"),
        java.util.List.of("starter drift"),
        "handoff to execution agents",
        "1.0",
        java.util.Map.of("phase", "foundation"),
        "",
        java.util.List.of());
    BlueprintPlan mockPayload = new BlueprintPlan(
        "success-test",
        "Small SaaS planning preview",
        "A starter-backed SaaS shell with explicit backend and frontend planning coverage.",
        "Single-tenant web delivery for admins managing billing and authentication.",
        java.util.List.of("Use approved SaaS starter", "Preserve starter boundaries"),
        java.util.List.of(
            new BlueprintPlan.WorkstreamDefinition(
                "Backend Core",
                "Ship auth and persistence",
                "Needed for secure access and billing data",
                java.util.List.of("auth", "billing", "postgres"),
                java.util.List.of(),
                java.util.List.of(),
                java.util.List.of("backend-engineer"),
                java.util.List.of(foundationPhase)),
            new BlueprintPlan.WorkstreamDefinition(
                "Frontend Dashboard",
                "Ship the starter-based web dashboard",
                "Needed to expose the product to admins",
                java.util.List.of("dashboard", "web ui"),
                java.util.List.of(),
                java.util.List.of("Backend Core"),
                java.util.List.of("frontend-engineer"),
                java.util.List.of(foundationPhase))),
        java.util.List.of("availability"),
        java.util.List.of("security"),
        java.util.List.of("tenant"),
        java.util.List.of(),
        java.util.List.of(),
        java.util.Map.of("contractVersion", BlueprintPlan.CURRENT_CONTRACT_VERSION),
        BlueprintPlan.CURRENT_CONTRACT_VERSION,
        java.util.List.of(foundationPhase),
        java.util.List.of(
            new BlueprintPlan.AgentDefinition("backend-engineer", "prompt", java.util.List.of("deliver backend workstream")),
            new BlueprintPlan.AgentDefinition("frontend-engineer", "prompt", java.util.List.of("deliver frontend workstream"))),
        "rules",
        "starter notes");

    when(promptAnalyzerService.analyzePrompt(any())).thenReturn(mockAnalysis);
    when(heavyModelResolutionService.resolve(
        org.mockito.ArgumentMatchers.any(UUID.class),
        org.mockito.ArgumentMatchers.nullable(String.class),
        org.mockito.ArgumentMatchers.nullable(com.arslanca.orchestrai.core.ai.provider.CredentialSource.class)))
        .thenReturn(resolvedHeavyModel());
    when(planningModelPolicyService.resolvePolicy(any(), any(), org.mockito.ArgumentMatchers.anyBoolean()))
        .thenReturn(new PlanningModelPolicy(
            PlanningMode.SINGLE_PASS,
            CapabilityRisk.LOW.value(),
            false,
            false,
            resolvedHeavyModel().modelKey(),
            ModelCapability.HIGH,
            false,
            DegradeStrategy.NONE.value()));
    when(orchestrationService.orchestratePrepared(any(), any(), any(), any(), any(), any(), any(GenerationUsageContext.class)))
        .thenReturn(new OrchestrationResult(
            BLUEPRINT_PLAN_COMPATIBILITY_ADAPTER.toCanonicalBlueprint(mockPayload),
            starterSelection()));
    when(blueprintGeneratorService.generateBlueprint(any(), any(), any(), any()))
        .thenReturn(BLUEPRINT_PLAN_COMPATIBILITY_ADAPTER.toCanonicalBlueprint(mockPayload));
    when(starterResolutionService.resolveStarter(any(), any())).thenReturn(starterSelection());
    when(scaffoldMaterializationService.materialize(any(), any(), any())).thenReturn(new MaterializedStarter(
        "nextjs-saas-shell",
        StarterSourceKind.REPO_PACK,
        "nextjs-saas-shell",
        java.util.List.of(),
        new MaterializationMetadata(0, 0L, 5L, java.util.List.of(), false, ""),
        false));

    ProjectRequest request = new ProjectRequest("success-test", "cursor", "Make a SaaS", null);
    SseEventPublisher publisher = mock(SseEventPublisher.class);

    // Act
    projectGenerationManager.generateBlueprint(testUser.getId(), request, publisher);

    // Assert
    await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
      Optional<ProjectEntity> project = projectRepository.findAll().stream()
          .filter(p -> p.getProjectName().equals("success-test"))
          .findFirst();

      assertThat(project).isPresent();

      // Should be COMPLETED
      assertThat(project.get().getStatus()).isEqualTo(ProjectStatus.COMPLETED);
      assertThat(project.get().getMetadata()).containsKey("resolvedHeavyModel");

      // Verify credit is permanently captured (down to 9 from initial 10)
      CreditEntity credit = creditRepository.findByUserId(testUser.getId()).orElseThrow();
      assertThat(credit.getBalance()).isEqualTo(9);
      assertThat(credit.getReservedCredits()).isEqualTo(0);
    });
  }

  private ResolvedHeavyModel resolvedHeavyModel() {
    return new ResolvedHeavyModel(
        "google:gemini-3-pro",
        "google",
        "gemini-3-pro",
        com.arslanca.orchestrai.core.ai.provider.CredentialSource.PLATFORM,
        "platform",
        "gemini-3-pro",
        "Gemini 3 Pro");
  }

  private StarterSelection starterSelection() {
    return new StarterSelection(
        java.util.UUID.randomUUID(),
        "success-test",
        "cursor",
        new StarterCandidate(
            "nextjs-saas-shell",
            "Next.js SaaS Shell",
            "nextjs",
            StarterSourceKind.REPO_PACK,
            "nextjs-saas-shell",
            java.util.List.of("nextjs", "react", "typescript"),
            "Matches nextjs and typescript for the detected project requirements."),
        java.util.List.of(),
        false,
        "");
  }
}
