package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.core.task.TaskExecutor;

import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.HeavyModelUserPreferenceService;
import com.arslanca.orchestrai.business.abstracts.PromptAnalyzerService;
import com.arslanca.orchestrai.business.abstracts.StarterResolutionService;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.ChatPlanningSnapshot;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatGenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.ChatPlanningStatusResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.enums.ChatPlanningHandoffState;
import com.arslanca.orchestrai.business.enums.ChatPlanningReadinessStatus;
import com.arslanca.orchestrai.business.planning.preparation.ChatPreparationContextService;
import com.arslanca.orchestrai.business.planning.preparation.PlanningSummaryFactory;
import com.arslanca.orchestrai.business.planning.preparation.PreparationSnapshotCodec;
import com.arslanca.orchestrai.business.planning.preparation.RequirementCompletenessPolicy;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatPlanningSnapshotRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ChatRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.MessageRepository;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.entity.concretes.ChatEntity;
import com.arslanca.orchestrai.entity.concretes.ChatPlanningSnapshotEntity;
import com.arslanca.orchestrai.entity.concretes.ConversationProjectEntity;
import com.arslanca.orchestrai.entity.concretes.MessageEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.enums.MessageRole;
import com.arslanca.orchestrai.entity.enums.ProjectGenerationPreparationStatus;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProjectGenerationPreparationManagerTest {

    @Mock
    private PromptAnalyzerService promptAnalyzerService;
    @Mock
    private HeavyModelResolutionService heavyModelResolutionService;
    @Mock
    private HeavyModelUserPreferenceService heavyModelUserPreferenceService;
    @Mock
    private StarterResolutionService starterResolutionService;
    @Mock
    private ChatPlanningSnapshotRepository snapshotRepository;
    @Mock
    private ChatRepository chatRepository;
    @Mock
    private MessageRepository messageRepository;
    @Mock
    private ProjectGenerationPreparationRepository preparationRepository;
    @Mock
    private ObjectProvider<TaskExecutor> namedExecutorProvider;
    @Mock
    private ObjectProvider<TaskExecutor> genericExecutorProvider;

    private ProjectGenerationPreparationManager manager;
    private PreparationSnapshotCodec preparationSnapshotCodec;
    private RequirementCompletenessPolicy completenessPolicy;
    private DeterministicPlanningPolicyManager deterministicPlanningPolicyManager;
    private SimpleMeterRegistry meterRegistry;
    private final AtomicReference<ChatPlanningSnapshotEntity> snapshotRef = new AtomicReference<>();
    private final List<ProjectGenerationPreparationEntity> preparationStore = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        meterRegistry = new SimpleMeterRegistry();
        preparationSnapshotCodec = new PreparationSnapshotCodec(new ObjectMapper());
        deterministicPlanningPolicyManager = new DeterministicPlanningPolicyManager();
        completenessPolicy = new RequirementCompletenessPolicy(deterministicPlanningPolicyManager);

        when(namedExecutorProvider.getIfAvailable()).thenReturn(new SyncTaskExecutor());
        when(genericExecutorProvider.getIfAvailable()).thenReturn(new SyncTaskExecutor());

        installSnapshotStore();
        installPreparationStore();

        manager = new ProjectGenerationPreparationManager(
                promptAnalyzerService,
                starterResolutionService,
                heavyModelResolutionService,
                heavyModelUserPreferenceService,
                completenessPolicy,
                new PlanningSummaryFactory(heavyModelResolutionService, new PlanningModelPolicyManager(), deterministicPlanningPolicyManager),
                preparationSnapshotCodec,
                new ChatPreparationContextService(
                        snapshotRepository,
                        chatRepository,
                        messageRepository,
                        promptAnalyzerService,
                        preparationSnapshotCodec),
                preparationRepository,
                meterRegistry,
                namedExecutorProvider,
                genericExecutorProvider);

        when(heavyModelResolutionService.resolve(any(UUID.class), any(), any())).thenReturn(new ResolvedHeavyModel(
                "google:gemini-3-pro",
                "google",
                "gemini-3-pro",
                CredentialSource.PLATFORM,
                "platform",
                "gemini-3-pro",
                "Gemini 3 Pro",
                false,
                false,
                null,
                null,
                null,
                List.of(),
                ModelCapability.HIGH));
        when(heavyModelUserPreferenceService.findPreference(any(UUID.class))).thenReturn(Optional.empty());
    }

    @Test
    @DisplayName("prepareGeneration should persist a prompt-backed preparation")
    void shouldPersistPromptBackedPreparation() {
        final UUID userId = UUID.randomUUID();
        final GenerationPreparationRequest request = new GenerationPreparationRequest(
                "Acme Platform",
                "cursor",
                "Build an internal SaaS planning workspace",
                "WEB");
        final StructuredAnalysis analysis = readyAnalysis("Acme Platform");

        when(promptAnalyzerService.analyzePrompt(userId, request.prompt())).thenReturn(analysis);
        when(starterResolutionService.resolveStarter(analysis, "cursor")).thenReturn(starterSelection());

        final GenerationPreparationResponse response = manager.prepareGeneration(userId, request);

        assertThat(response.projectName()).isEqualTo("Acme Platform");
        assertThat(response.planningSummary()).isNotNull();
        assertThat(response.planningSummary().complexity()).isEqualTo(PlanningComplexity.COMPLEX);
        assertThat(response.planningSummary().selectedPlanningMode()).isNotNull();
        assertThat(meterRegistry.counter(
                "project_generation.prepare.request",
                "sourceChannel", "web",
                "outcome", "success",
                "reason", "prepared").count()).isEqualTo(1.0d);
    }

    @Test
    @DisplayName("evaluateChatPlanningAfterAssistantTurn should queue analysis and persist a ready preparation")
    void shouldQueueAnalysisAndPersistPreparation() {
        final UUID userId = UUID.randomUUID();
        final UUID chatId = UUID.randomUUID();
        final ChatEntity chat = chat(chatId, false, "Planning Workspace");
        final StructuredAnalysis analysis = readyAnalysis("Planning Workspace");

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(chat));
        when(messageRepository.countByChatId(chatId)).thenReturn(2L);
        when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(descendingRecentMessages());
        when(messageRepository.findByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(chronologicalMessages());
        when(promptAnalyzerService.refreshChatPlanningSnapshot(eq(userId), any(), anyString())).thenReturn(readySnapshot());
        when(promptAnalyzerService.analyzePlanningConversation(eq(userId), anyString())).thenReturn(analysis);
        when(starterResolutionService.resolveStarter(analysis, "cursor")).thenReturn(starterSelection());

        final ChatPlanningStatusResponse response = manager.evaluateChatPlanningAfterAssistantTurn(userId, chatId, "cursor");
        final ChatPlanningStatusResponse persistedStatus = manager.getChatPlanningStatus(userId, chatId);

        assertThat(response.status()).isEqualTo(ChatPlanningReadinessStatus.ANALYZING);
        assertThat(persistedStatus.status()).isEqualTo(ChatPlanningReadinessStatus.READY_FOR_PREPARATION);
        assertThat(persistedStatus.preparation()).isNotNull();
        assertThat(persistedStatus.preparation().projectName()).isEqualTo("Planning Workspace");
        assertThat(preparationStore).hasSize(1);
    }

    @Test
    @DisplayName("getChatPlanningStatus should expose analysis failures for the latest assistant turn")
    void shouldExposeAnalysisFailure() {
        final UUID userId = UUID.randomUUID();
        final UUID chatId = UUID.randomUUID();
        final ChatEntity chat = chat(chatId, false, "Planning Workspace");

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(chat));
        when(messageRepository.countByChatId(chatId)).thenReturn(2L);
        when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(descendingRecentMessages());
        when(messageRepository.findByChatIdOrderByCreatedAtAsc(chatId)).thenReturn(chronologicalMessages());
        when(promptAnalyzerService.refreshChatPlanningSnapshot(eq(userId), any(), anyString()))
                .thenThrow(new com.arslanca.orchestrai.core.exception.type.LlmResponseParseException("bad json"));

        manager.evaluateChatPlanningAfterAssistantTurn(userId, chatId, "cursor");
        final ChatPlanningStatusResponse response = manager.getChatPlanningStatus(userId, chatId);

        assertThat(response.ready()).isFalse();
        assertThat(response.status()).isEqualTo(ChatPlanningReadinessStatus.ANALYSIS_FAILED);
        assertThat(response.reason()).contains("bad json");
    }

    @Test
    @DisplayName("prepareGenerationFromChat should reuse the current prepared response when ide target matches")
    void shouldReusePreparedResponseForMatchingIdeTarget() {
        final UUID userId = UUID.randomUUID();
        final UUID chatId = UUID.randomUUID();
        final ChatEntity chat = chat(chatId, false, "Planning Workspace");
        final ProjectGenerationPreparationEntity existingPreparation = persistExistingPreparation(
                userId,
                chatId,
                2L,
                "cursor",
                readyAnalysis("Planning Workspace"),
                "Planning summary");

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(chat));
        when(messageRepository.countByChatId(chatId)).thenReturn(2L);

        final GenerationPreparationResponse response = manager.prepareGenerationFromChat(
                userId,
                chatId,
                new ChatGenerationPreparationRequest("cursor"));

        assertThat(response.preparationId()).isEqualTo(existingPreparation.getId());
        verify(promptAnalyzerService, never()).refreshChatPlanningSnapshot(any(), anyString());
    }

    @Test
    @DisplayName("prepareGenerationFromChat should clone an existing prepared analysis for a different ide target")
    void shouldClonePreparedAnalysisForDifferentIdeTarget() {
        final UUID userId = UUID.randomUUID();
        final UUID chatId = UUID.randomUUID();
        final ChatEntity chat = chat(chatId, false, "Planning Workspace");
        final StructuredAnalysis analysis = readyAnalysis("Planning Workspace");

        persistExistingPreparation(userId, chatId, 2L, "generic", analysis, "Planning summary");
        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(chat));
        when(messageRepository.countByChatId(chatId)).thenReturn(2L);
        when(starterResolutionService.resolveStarter(analysis, "cursor")).thenReturn(starterSelection());

        final GenerationPreparationResponse response = manager.prepareGenerationFromChat(
                userId,
                chatId,
                new ChatGenerationPreparationRequest("cursor"));

        assertThat(response.ideTarget()).isEqualTo("cursor");
        assertThat(preparationStore).hasSize(2);
    }

    @Test
    @DisplayName("prepareGenerationFromChat should fail fast while planning analysis is still running")
    void shouldFailWhilePlanningAnalysisIsRunning() {
        final UUID userId = UUID.randomUUID();
        final UUID chatId = UUID.randomUUID();
        final ChatEntity chat = chat(chatId, false, "Planning Workspace");
        final ChatPlanningSnapshotEntity snapshotEntity = ChatPlanningSnapshotEntity.create(
                chat,
                0L,
                ChatPlanningSnapshot.CURRENT_CONTRACT_VERSION,
                null);
        snapshotEntity.markAnalysisRequested(2L, "Analyzing the latest planning handoff.");
        snapshotRef.set(snapshotEntity);

        when(chatRepository.findByIdAndUserId(chatId, userId)).thenReturn(Optional.of(chat));
        when(messageRepository.countByChatId(chatId)).thenReturn(2L);
        when(messageRepository.findRecentMessagesByChatId(chatId, 20)).thenReturn(descendingRecentMessages());

        assertThatThrownBy(() -> manager.prepareGenerationFromChat(userId, chatId, new ChatGenerationPreparationRequest("cursor")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Analyzing the latest planning handoff");
    }

    @Test
    @DisplayName("prepareGeneration should fail hard when an external selected route cannot be bound")
    void shouldFailHardWhenExternalSelectedRouteCannotBeBound() {
        final UUID userId = UUID.randomUUID();
        final GenerationPreparationRequest request = new GenerationPreparationRequest(
                "Acme Platform",
                "cursor",
                "Build an external-route bound workspace",
                "WEB");
        final StructuredAnalysis analysis = readyAnalysis("Acme Platform");

        when(promptAnalyzerService.analyzePrompt(userId, request.prompt())).thenReturn(analysis);
        when(starterResolutionService.resolveStarter(analysis, "cursor")).thenReturn(starterSelection());
        when(heavyModelUserPreferenceService.findPreference(userId)).thenReturn(Optional.of(
                new com.arslanca.orchestrai.business.dtos.ai.HeavyModelPreference(
                        "github:gpt-4.1",
                        CredentialSource.USER_LINKED,
                        UUID.randomUUID())));
        when(heavyModelResolutionService.resolve(userId, null, null)).thenThrow(
                com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException.serviceUnavailable(
                        "Secilen user-linked AI connection artik gecerli degil veya yeniden yetkilendirme gerektiriyor."));

        assertThatThrownBy(() -> manager.prepareGeneration(userId, request))
                .isInstanceOf(com.arslanca.orchestrai.core.exception.type.HeavyModelResolutionException.class)
                .hasMessageContaining("user-linked AI connection");

        verify(preparationRepository, never()).save(any(ProjectGenerationPreparationEntity.class));
    }

    private void installSnapshotStore() {
        when(snapshotRepository.findByChatId(any(UUID.class))).thenAnswer(invocation -> {
            final UUID chatId = invocation.getArgument(0);
            final ChatPlanningSnapshotEntity entity = snapshotRef.get();
            if (entity != null && entity.getChat().getId().equals(chatId)) {
                return Optional.of(entity);
            }
            return Optional.empty();
        });
        when(snapshotRepository.save(any(ChatPlanningSnapshotEntity.class))).thenAnswer(invocation -> {
            final ChatPlanningSnapshotEntity entity = invocation.getArgument(0);
            snapshotRef.set(entity);
            return entity;
        });
    }

    private void installPreparationStore() throws Exception {
        when(preparationRepository.save(any(ProjectGenerationPreparationEntity.class))).thenAnswer(invocation -> {
            final ProjectGenerationPreparationEntity entity = invocation.getArgument(0);
            if (entity.getId() == null) {
                final Field idField = ProjectGenerationPreparationEntity.class.getDeclaredField("id");
                idField.setAccessible(true);
                idField.set(entity, UUID.randomUUID());
            }
            preparationStore.removeIf(existing -> existing.getId().equals(entity.getId()));
            preparationStore.add(entity);
            return entity;
        });
        when(preparationRepository.findFirstByUserIdAndChatIdAndChatMessageCountAndIdeTargetAndStatusOrderByCreatedAtDesc(
                any(UUID.class), any(UUID.class), any(Long.class), anyString(), any(ProjectGenerationPreparationStatus.class)))
                .thenAnswer(invocation -> preparationStore.stream()
                        .filter(entity -> entity.getUserId().equals(invocation.getArgument(0))
                                && entity.getChatId().equals(invocation.getArgument(1))
                                && entity.getChatMessageCount().equals(invocation.getArgument(2))
                                && entity.getIdeTarget().equals(invocation.getArgument(3))
                                && entity.getStatus() == invocation.getArgument(4))
                        .reduce((first, second) -> second));
        when(preparationRepository.findFirstByUserIdAndChatIdAndChatMessageCountAndStatusOrderByCreatedAtDesc(
                any(UUID.class), any(UUID.class), any(Long.class), any(ProjectGenerationPreparationStatus.class)))
                .thenAnswer(invocation -> preparationStore.stream()
                        .filter(entity -> entity.getUserId().equals(invocation.getArgument(0))
                                && entity.getChatId().equals(invocation.getArgument(1))
                                && entity.getChatMessageCount().equals(invocation.getArgument(2))
                                && entity.getStatus() == invocation.getArgument(3))
                        .reduce((first, second) -> second));
    }

    private ProjectGenerationPreparationEntity persistExistingPreparation(
            final UUID userId,
            final UUID chatId,
            final long chatMessageCount,
            final String ideTarget,
            final StructuredAnalysis analysis,
            final String promptSummary) {
        final StarterSelection starterSelection = starterSelection();
        final PlanningSummaryFactory summaryFactory = new PlanningSummaryFactory(
                heavyModelResolutionService,
                new PlanningModelPolicyManager(),
                deterministicPlanningPolicyManager);
        final RequirementCompletenessPolicy policy = new RequirementCompletenessPolicy(deterministicPlanningPolicyManager);
        final ProjectGenerationPreparationEntity entity = ProjectGenerationPreparationEntity.create(
                userId,
                chatId,
                chatMessageCount,
                "CHAT",
                analysis.projectName(),
                ideTarget,
                promptSummary,
                preparationSnapshotCodec.toPayload(analysis),
                preparationSnapshotCodec.toPayload(starterSelection),
                analysis.contractVersion(),
                preparationSnapshotCodec.buildPreparationSnapshot(
                        analysis,
                        summaryFactory.buildPlanningSummary(analysis, starterSelection, null),
                        starterSelection,
                        policy.evaluateRequirementCompleteness(analysis),
                        null,
                        Optional.empty(),
                        chatMessageCount));
        try {
            final Field idField = ProjectGenerationPreparationEntity.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(entity, UUID.randomUUID());
        } catch (final Exception exception) {
            throw new IllegalStateException("Failed to seed preparation fixture", exception);
        }
        preparationStore.add(entity);
        return entity;
    }

    private ChatEntity chat(final UUID chatId, final boolean autoNamed, final String projectName) {
        final ConversationProjectEntity project = ConversationProjectEntity.builder()
                .id(UUID.randomUUID())
                .name(projectName)
                .isAutoNamed(autoNamed)
                .build();
        return ChatEntity.builder()
                .id(chatId)
                .conversationProject(project)
                .build();
    }

    private List<MessageEntity> descendingRecentMessages() {
        return List.of(
                MessageEntity.builder().role(MessageRole.ASSISTANT).content("The plan is complete and ready for blueprint generation.").build(),
                MessageEntity.builder().role(MessageRole.USER).content("Everything is complete, continue to the blueprint.").build());
    }

    private List<MessageEntity> chronologicalMessages() {
        return List.of(
                MessageEntity.builder().role(MessageRole.USER).content("Everything is complete, continue to the blueprint.").build(),
                MessageEntity.builder().role(MessageRole.ASSISTANT).content("The plan is complete and ready for blueprint generation.").build());
    }

    private StructuredAnalysis readyAnalysis(final String projectName) {
        return new StructuredAnalysis(
                projectName,
                "analytics api",
                List.of("go", "postgres", "redis"),
                List.of("tenant management", "event ingestion", "analytics query api"),
                "complex",
                false,
                "",
                "Backend-only, multi-tenant analytics API with asynchronous event processing.",
                List.of(),
                List.of(),
                List.of("company admin provisions crew members", "clients send analytics events"),
                List.of("api"),
                false,
                true,
                true,
                "self-hosted docker",
                "multi-tenant",
                List.of("postgres", "redis"),
                List.of(),
                List.of("tenant-scoped api tokens"),
                List.of(),
                "high ingest throughput",
                "low-latency reads for analytics summaries",
                List.of("customer-managed local infrastructure"),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of("multi-tenant", "async processing", "worker service separation"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    }

    private ChatPlanningSnapshot readySnapshot() {
        return new ChatPlanningSnapshot(
                "Planning Workspace",
                "analytics api",
                "Backend-only multi-tenant analytics backend with async processing.",
                "Go, PostgreSQL, Redis",
                "B2B companies and their admin crews",
                List.of("multi-tenant analytics api", "self-hosted docker deployment"),
                List.of("go", "postgres", "redis"),
                List.of(),
                List.of(),
                List.of("multi-tenant", "async processing"),
                ChatPlanningHandoffState.READY_FOR_PREPARATION,
                "Planning conversation is ready for generation review.",
                ChatPlanningSnapshot.CURRENT_CONTRACT_VERSION);
    }

    private StarterSelection starterSelection() {
        return new StarterSelection(
                UUID.randomUUID(),
                "Planning Workspace",
                "cursor",
                new StarterCandidate(
                        "go-api-core",
                        "Go API Core",
                        "api",
                        StarterSourceKind.REPO_PACK,
                        "repo-pack",
                        List.of("go", "postgres", "redis"),
                        "Best match for the requested stack."),
                List.of(),
                false,
                "");
    }
}
