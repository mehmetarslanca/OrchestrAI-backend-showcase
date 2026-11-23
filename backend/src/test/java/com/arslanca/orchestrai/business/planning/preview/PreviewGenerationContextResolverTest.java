package com.arslanca.orchestrai.business.planning.preview;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.abstracts.HeavyModelResolutionService;
import com.arslanca.orchestrai.business.abstracts.PlanningModelPolicyService;
import com.arslanca.orchestrai.business.abstracts.StarterSelectionResolver;
import com.arslanca.orchestrai.business.concretes.DeterministicPlanningPolicyManager;
import com.arslanca.orchestrai.business.concretes.ResolvedHeavyModelRuntimeBindingManager;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreviewRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.planning.lifecycle.PlanningLifecycleAuthority;
import com.arslanca.orchestrai.business.planning.preparation.PreparationSnapshotCodec;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectGenerationPreparationRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class PreviewGenerationContextResolverTest {

    @Mock
    private ProjectGenerationPreparationRepository preparationRepository;
    @Mock
    private StarterSelectionResolver starterSelectionResolver;
    @Mock
    private HeavyModelResolutionService heavyModelResolutionService;
    @Mock
    private ResolvedHeavyModelRuntimeBindingManager resolvedHeavyModelRuntimeBindingManager;
    @Mock
    private PlanningModelPolicyService planningModelPolicyService;
    @Mock
    private PlanningLifecycleAuthority planningLifecycleAuthority;

    @Test
    void shouldResolvePreviewGenerationContextWhenPolicyLooksLight() {
        final PreparationSnapshotCodec preparationSnapshotCodec = new PreparationSnapshotCodec(new ObjectMapper());
        final DeterministicPlanningPolicyManager deterministicPlanningPolicyManager = new DeterministicPlanningPolicyManager();
        final PreviewGenerationContextResolver resolver = new PreviewGenerationContextResolver(
                preparationRepository,
                starterSelectionResolver,
                heavyModelResolutionService,
                resolvedHeavyModelRuntimeBindingManager,
                planningModelPolicyService,
                deterministicPlanningPolicyManager,
                planningLifecycleAuthority,
                preparationSnapshotCodec);
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "billing"),
                "complex",
                false,
                "",
                "Complex orchestration platform");
        final StarterSelection starterSelection = new StarterSelection(
                UUID.randomUUID(),
                "Acme Platform",
                "cursor",
                new StarterCandidate(
                        "nextjs-saas-shell",
                        "Next.js SaaS Shell",
                        "saas",
                        StarterSourceKind.REPO_PACK,
                        "repo-pack",
                        List.of("nextjs", "postgres"),
                        "Best match."),
                List.of(),
                false,
                "");
        final ProjectGenerationPreparationEntity preparation = ProjectGenerationPreparationEntity.create(
                userId,
                null,
                null,
                "WEB",
                "Acme Platform",
                "cursor",
                "Build Acme Platform",
                preparationSnapshotCodec.toPayload(analysis),
                preparationSnapshotCodec.toPayload(starterSelection),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION,
                java.util.Map.of());
        final ResolvedHeavyModel resolvedHeavyModel = new ResolvedHeavyModel(
                "ollama:deepseek-r1",
                "ollama",
                "deepseek",
                com.arslanca.orchestrai.core.ai.provider.CredentialSource.PLATFORM,
                "platform",
                "deepseek-r1",
                "DeepSeek R1",
                false,
                false,
                null,
                null,
                null,
                List.of("LOCAL"),
                ModelCapability.LOW);

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(java.util.Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(starterSelection, null)).thenReturn(starterSelection);
        when(heavyModelResolutionService.resolve(userId, null, null)).thenReturn(resolvedHeavyModel);
        final DeterministicPlanningProfile planningProfile = deterministicPlanningPolicyManager.deriveProfile(analysis);
        when(planningModelPolicyService.resolvePolicy(planningProfile.effectiveComplexity(), resolvedHeavyModel, false)).thenReturn(
                new PlanningModelPolicy(
                        PlanningMode.REDUCED_MULTI_PASS,
                        "high",
                        true,
                        false,
                        resolvedHeavyModel.modelKey(),
                        resolvedHeavyModel.capability(),
                        true,
                        "stronger_model_required"));

        final PreviewGenerationContext context = resolver.resolve(userId, new GenerationPreviewRequest(preparationId, null));

        assertThat(context.preparation()).isEqualTo(preparation);
        assertThat(context.resolvedHeavyModel()).isEqualTo(resolvedHeavyModel);
        assertThat(context.planningModelPolicy().degradeStrategy().value()).isEqualTo("stronger_model_required");
        verify(planningLifecycleAuthority).ensurePreviewGenerationAllowed(preparation);
        verify(resolvedHeavyModelRuntimeBindingManager).bind(resolvedHeavyModel);
    }

    @Test
    void shouldUseSnapshotBoundHeavyModelForPreviewContext() {
        final PreparationSnapshotCodec preparationSnapshotCodec = new PreparationSnapshotCodec(new ObjectMapper());
        final DeterministicPlanningPolicyManager deterministicPlanningPolicyManager = new DeterministicPlanningPolicyManager();
        final PreviewGenerationContextResolver resolver = new PreviewGenerationContextResolver(
                preparationRepository,
                starterSelectionResolver,
                heavyModelResolutionService,
                resolvedHeavyModelRuntimeBindingManager,
                planningModelPolicyService,
                deterministicPlanningPolicyManager,
                planningLifecycleAuthority,
                preparationSnapshotCodec);
        final UUID userId = UUID.randomUUID();
        final UUID preparationId = UUID.randomUUID();
        final StructuredAnalysis analysis = new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "billing"),
                "complex",
                false,
                "",
                "Complex orchestration platform");
        final StarterSelection starterSelection = new StarterSelection(
                UUID.randomUUID(),
                "Acme Platform",
                "cursor",
                new StarterCandidate(
                        "nextjs-saas-shell",
                        "Next.js SaaS Shell",
                        "saas",
                        StarterSourceKind.REPO_PACK,
                        "repo-pack",
                        List.of("nextjs", "postgres"),
                        "Best match."),
                List.of(),
                false,
                "");
        final ResolvedHeavyModel snapshotBoundHeavyModel = new ResolvedHeavyModel(
                "github:gpt-4.1",
                "github",
                "gpt-4.1",
                com.arslanca.orchestrai.core.ai.provider.CredentialSource.USER_LINKED,
                userId.toString(),
                "openai/gpt-4.1",
                "GPT-4.1",
                false,
                false,
                null,
                "github",
                "github-models",
                List.of("USER_LINKED"),
                ModelCapability.HIGH,
                UUID.randomUUID(),
                com.arslanca.orchestrai.entity.enums.AiConnectionExecutionClass.USER_SINGLE_ROUTE,
                com.arslanca.orchestrai.entity.enums.AiConnectionType.GITHUB_MODELS_USER_LINKED);
        final ProjectGenerationPreparationEntity preparation = ProjectGenerationPreparationEntity.create(
                userId,
                null,
                null,
                "WEB",
                "Acme Platform",
                "cursor",
                "Build Acme Platform",
                preparationSnapshotCodec.toPayload(analysis),
                preparationSnapshotCodec.toPayload(starterSelection),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION,
                preparationSnapshotCodec.buildPreparationSnapshot(
                        analysis,
                        null,
                        starterSelection,
                        null,
                        snapshotBoundHeavyModel,
                        java.util.Optional.empty(),
                        null));

        when(preparationRepository.findByIdAndUserId(preparationId, userId)).thenReturn(java.util.Optional.of(preparation));
        when(starterSelectionResolver.resolveSelectedStarter(starterSelection, null)).thenReturn(starterSelection);
        final DeterministicPlanningProfile planningProfile = deterministicPlanningPolicyManager.deriveProfile(analysis);
        when(planningModelPolicyService.resolvePolicy(planningProfile.effectiveComplexity(), snapshotBoundHeavyModel, false)).thenReturn(
                new PlanningModelPolicy(
                        PlanningMode.MULTI_PASS_EXPANSION,
                        "high",
                        true,
                        true,
                        snapshotBoundHeavyModel.modelKey(),
                        snapshotBoundHeavyModel.capability(),
                        false,
                        "none"));

        final PreviewGenerationContext context = resolver.resolve(userId, new GenerationPreviewRequest(preparationId, null));

        assertThat(context.resolvedHeavyModel()).isEqualTo(snapshotBoundHeavyModel);
        verify(resolvedHeavyModelRuntimeBindingManager).bind(snapshotBoundHeavyModel);
        verify(heavyModelResolutionService, never()).resolve(userId, null, null);
    }
}
