package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import com.arslanca.orchestrai.business.planning.lifecycle.PreviewClaim;
import com.arslanca.orchestrai.business.planning.lifecycle.PreviewReusePolicy;
import com.arslanca.orchestrai.business.planning.lifecycle.LifecycleViolationException;
import com.arslanca.orchestrai.business.planning.lifecycle.LifecycleViolationReason;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PreviewReuseDisposition;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationSourceChannel;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.planning.governance.DefaultPlanningBudgetPolicy;
import com.arslanca.orchestrai.business.planning.governance.DefaultPlanningGovernanceService;
import com.arslanca.orchestrai.business.planning.governance.DefaultPolicyVersionFingerprintResolver;
import com.arslanca.orchestrai.business.planning.governance.DefaultPreviewBillingPolicy;
import com.arslanca.orchestrai.business.planning.governance.DefaultPreviewCachePolicy;
import com.arslanca.orchestrai.business.planning.governance.NoOpPlanningTelemetryService;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshotCodec;
import com.arslanca.orchestrai.business.planning.governance.PlanningRolloutPolicy;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.business.planning.governance.PropertyBackedPlanningRolloutPolicy;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;
import com.arslanca.orchestrai.core.exception.type.PreparationStateException;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

class PreviewReusePolicyTest {

    @Mock
    private ProjectPlanningPreviewRepository planningPreviewRepository;

    @Mock
    private PlanningTelemetryService planningTelemetryService;

    private PreviewReusePolicy previewReusePolicy;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        final ObjectMapper objectMapper = new ObjectMapper();
        final PlanningGovernanceProperties properties = new PlanningGovernanceProperties();
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(properties);
        final DefaultPlanningBudgetPolicy budgetPolicy = new DefaultPlanningBudgetPolicy();
        final DefaultPreviewBillingPolicy billingPolicy = new DefaultPreviewBillingPolicy();
        final DefaultPolicyVersionFingerprintResolver resolver = new DefaultPolicyVersionFingerprintResolver(
                budgetPolicy,
                billingPolicy,
                rolloutPolicy);
        previewReusePolicy = new PreviewReusePolicy(
                planningPreviewRepository,
                new DefaultPlanningGovernanceService(
                        budgetPolicy,
                        billingPolicy,
                        new DefaultPreviewCachePolicy(objectMapper, resolver, properties),
                        new NoOpPlanningTelemetryService(),
                        rolloutPolicy,
                        resolver),
                new PlanningGovernanceSnapshotCodec(objectMapper),
                planningTelemetryService);
    }

    @Test
    void shouldTreatBlankAndDocsOnlyStarterKeysAsPreparationReuse() {
        assertThat(previewReusePolicy.dispositionFor(null)).isEqualTo(PreviewReuseDisposition.REUSE_PREPARATION_SELECTION);
        assertThat(previewReusePolicy.dispositionFor("docs_only")).isEqualTo(PreviewReuseDisposition.REUSE_PREPARATION_SELECTION);
        assertThat(previewReusePolicy.dispositionFor("nextjs-saas-shell")).isEqualTo(PreviewReuseDisposition.RESOLVE_ACCEPTED_STARTER);
    }

    @Test
    void shouldEmitLegacyDocsOnlySentinelForFallbackSelections() {
        final StarterSelection docsOnlySelection = new StarterSelection(
                UUID.randomUUID(),
                "Acme Platform",
                "cursor",
                null,
                List.of(),
                true,
                "No approved starter matched.");
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

        assertThat(previewReusePolicy.selectedStarterKeyFor(docsOnlySelection)).isEqualTo("docs_only");
        assertThat(previewReusePolicy.selectedStarterKeyFor(starterSelection)).isEqualTo("nextjs-saas-shell");
    }

    @Test
    void shouldReuseSameCachePreviewAfterInitialDraftCompletes() {
        final ProjectGenerationPreparationEntity preparation = preparationEntity();
        final StarterSelection starterSelection = starterSelection();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                "google:gemini-3-pro",
                ModelCapability.HIGH,
                false,
                "none");
        final ProjectPlanningPreviewEntity createdDraft = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                "placeholder-cache",
                com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview.CURRENT_CONTRACT_VERSION);

        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparation.getId()))
                .thenReturn(List.of())
                .thenReturn(List.of(createdDraft));
        when(planningPreviewRepository.save(org.mockito.ArgumentMatchers.any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> {
                    final ProjectPlanningPreviewEntity saved = invocation.getArgument(0);
                    writePreviewField(createdDraft, "previewCacheKey", saved.getPreviewCacheKey());
                    writePreviewField(createdDraft, "selectedStarterKey", saved.getSelectedStarterKey());
                    writePreviewField(createdDraft, "planningMode", saved.getPlanningMode());
                    writePreviewField(createdDraft, "governanceSnapshotJson", saved.getGovernanceSnapshotJson());
                    return createdDraft;
                });

        final PreviewClaim initialClaim = previewReusePolicy.claimPreview(
                preparation,
                structuredAnalysis(),
                planningProfile(),
                starterSelection,
                planningModelPolicy);
        createdDraft.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        final PreviewClaim reusableClaim = previewReusePolicy.claimPreview(preparation, structuredAnalysis(), planningProfile(), starterSelection, planningModelPolicy);

        assertThat(initialClaim.requiresPlanning()).isTrue();
        assertThat(reusableClaim.requiresPlanning()).isFalse();
        assertThat(reusableClaim.preview().getPreviewStatus()).isEqualTo(PlanningPreviewStatus.READY_FOR_REVIEW);
    }

    @Test
    void shouldRejectClaimWhenAnyDraftAlreadyExists() {
        final ProjectGenerationPreparationEntity preparation = preparationEntity();
        final ProjectPlanningPreviewEntity existingDraft = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "other-starter",
                PlanningMode.MULTI_PASS_EXPANSION,
                "other-cache",
                com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview.CURRENT_CONTRACT_VERSION);

        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparation.getId()))
                .thenReturn(List.of(existingDraft));

        assertThatThrownBy(() -> previewReusePolicy.claimPreview(
                preparation,
                structuredAnalysis(),
                planningProfile(),
                starterSelection(),
                new PlanningModelPolicy(
                        PlanningMode.MULTI_PASS_EXPANSION,
                        "elevated",
                        true,
                        true,
                        "google:gemini-3-pro",
                        ModelCapability.HIGH,
                        false,
                        "none")))
                .isInstanceOf(PreparationStateException.class)
                .hasMessageContaining("currently being generated");
    }

    @Test
    void shouldInvalidateStaleDraftAndCreateNewClaim() {
        final ProjectGenerationPreparationEntity preparation = preparationEntity();
        final ProjectPlanningPreviewEntity staleDraft = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "other-starter",
                PlanningMode.MULTI_PASS_EXPANSION,
                "other-cache",
                com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview.CURRENT_CONTRACT_VERSION);
        writePreviewField(staleDraft, "createdAt", OffsetDateTime.now().minusMinutes(15));

        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparation.getId()))
                .thenReturn(List.of(staleDraft));
        when(planningPreviewRepository.save(org.mockito.ArgumentMatchers.any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final PreviewClaim claim = previewReusePolicy.claimPreview(
                preparation,
                structuredAnalysis(),
                planningProfile(),
                starterSelection(),
                new PlanningModelPolicy(
                        PlanningMode.MULTI_PASS_EXPANSION,
                        "elevated",
                        true,
                        true,
                        "google:gemini-3-pro",
                        ModelCapability.HIGH,
                        false,
                        "none"));

        assertThat(staleDraft.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        assertThat(claim.requiresPlanning()).isTrue();
        verify(planningTelemetryService).recordPreviewInvalidation(
                eq(null),
                eq(PlanningPreviewStatus.DRAFT),
                eq("stale_draft_recovered"));
    }

    @Test
    void shouldIgnoreExpiredReusablePreviewAndCreateNewDraft() {
        final ProjectGenerationPreparationEntity preparation = preparationEntity();
        final StarterSelection starterSelection = starterSelection();
        final PlanningModelPolicy planningModelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                "google:gemini-3-pro",
                ModelCapability.HIGH,
                false,
                "none");
        final ProjectPlanningPreviewEntity existingPreview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                "placeholder-cache",
                PlanningPreview.CURRENT_CONTRACT_VERSION);

        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparation.getId()))
                .thenReturn(List.of())
                .thenReturn(List.of(existingPreview));
        when(planningPreviewRepository.save(org.mockito.ArgumentMatchers.any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> {
                    final ProjectPlanningPreviewEntity saved = invocation.getArgument(0);
                    writePreviewField(existingPreview, "previewCacheKey", saved.getPreviewCacheKey());
                    writePreviewField(existingPreview, "selectedStarterKey", saved.getSelectedStarterKey());
                    writePreviewField(existingPreview, "planningMode", saved.getPlanningMode());
                    writePreviewField(existingPreview, "governanceSnapshotJson", saved.getGovernanceSnapshotJson());
                    return existingPreview;
                });

        final PreviewClaim initialClaim = previewReusePolicy.claimPreview(
                preparation,
                structuredAnalysis(),
                planningProfile(),
                starterSelection,
                planningModelPolicy);
        existingPreview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        writePreviewField(existingPreview, "createdAt", OffsetDateTime.now().minusMinutes(5));
        writePreviewField(existingPreview, "governanceSnapshotJson", Map.of(
                "cacheDecision", Map.of(
                        "previewCacheKey", existingPreview.getPreviewCacheKey(),
                        "plannerModelFingerprint", "google:gemini-3-pro|high|remote",
                        "governancePolicyFingerprint", "fingerprint",
                        "reviewReadyReuseTtlSeconds", 1,
                        "acceptedPreviewReuseTtlSeconds", 1,
                        "cacheable", true)));

        final PreviewClaim renewedClaim = previewReusePolicy.claimPreview(
                preparation,
                structuredAnalysis(),
                planningProfile(),
                starterSelection,
                planningModelPolicy);

        assertThat(initialClaim.requiresPlanning()).isTrue();
        assertThat(existingPreview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        assertThat(renewedClaim.requiresPlanning()).isTrue();
    }

    @Test
    void shouldRejectPolicyDriftedAcceptedPreviewAtStart() {
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(planningPreviewRepository.save(org.mockito.ArgumentMatchers.any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final ProjectGenerationPreparationEntity preparation = preparationEntity();
        final StarterSelection starterSelection = starterSelection();
        final ProjectPlanningPreviewEntity acceptedPreview = previewReusePolicy.claimPreview(
                preparation,
                structuredAnalysis(),
                planningProfile(),
                starterSelection,
                planningPolicy(PlanningMode.MULTI_PASS_EXPANSION, "none")).preview();
        acceptedPreview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        acceptedPreview.markAccepted();

        assertLifecycleViolation(
                () -> previewReusePolicy.ensureAcceptedPreviewReusableForStart(
                        preparation,
                        acceptedPreview,
                        structuredAnalysis(),
                        planningProfile(),
                        starterSelection,
                        planningPolicy(PlanningMode.REDUCED_MULTI_PASS, "stronger_model_required")),
                LifecycleViolationReason.ACCEPTED_PREVIEW_POLICY_DRIFT);
        assertThat(acceptedPreview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        assertThat(acceptedPreview.getRepairStatusJson()).containsEntry("reason", "accepted_preview_policy_drift");
        verify(planningTelemetryService).recordPreviewInvalidation(
                org.mockito.ArgumentMatchers.any(),
                eq(PlanningPreviewStatus.ACCEPTED),
                eq("accepted_preview_policy_drift"));
    }

    @Test
    void shouldRejectFingerprintMismatchedAcceptedPreviewAtStart() {
        when(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        when(planningPreviewRepository.save(org.mockito.ArgumentMatchers.any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final ProjectGenerationPreparationEntity preparation = preparationEntity();
        final StarterSelection starterSelection = starterSelection();
        final ProjectPlanningPreviewEntity acceptedPreview = previewReusePolicy.claimPreview(
                preparation,
                structuredAnalysis(),
                planningProfile(),
                starterSelection,
                planningPolicy(PlanningMode.MULTI_PASS_EXPANSION, "none")).preview();
        acceptedPreview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        acceptedPreview.markAccepted();

        assertLifecycleViolation(
                () -> previewReusePolicy.ensureAcceptedPreviewReusableForStart(
                        preparation,
                        acceptedPreview,
                        structuredAnalysis(),
                        expandedPlanningProfile(),
                        starterSelection,
                        planningPolicy(PlanningMode.MULTI_PASS_EXPANSION, "none")),
                LifecycleViolationReason.ACCEPTED_PREVIEW_FINGERPRINT_MISMATCH);
        assertThat(acceptedPreview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        assertThat(acceptedPreview.getRepairStatusJson()).containsEntry("reason", "accepted_preview_fingerprint_mismatch");
    }

    @Test
    void shouldRejectUnreadableAcceptedGovernanceSnapshotAtStart() {
        when(planningPreviewRepository.save(org.mockito.ArgumentMatchers.any(ProjectPlanningPreviewEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        final ProjectGenerationPreparationEntity preparation = preparationEntity();
        final ProjectPlanningPreviewEntity acceptedPreview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                PlanningPreview.CURRENT_CONTRACT_VERSION);
        acceptedPreview.attachGovernanceSnapshot(Map.of(
                "cacheDecision", java.util.List.of("bad"),
                "planningPolicyFingerprint", java.util.List.of("bad")));
        acceptedPreview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        acceptedPreview.markAccepted();

        assertLifecycleViolation(
                () -> previewReusePolicy.ensureAcceptedPreviewReusableForStart(
                        preparation,
                        acceptedPreview,
                        structuredAnalysis(),
                        planningProfile(),
                        starterSelection(),
                        planningPolicy(PlanningMode.MULTI_PASS_EXPANSION, "none")),
                LifecycleViolationReason.ACCEPTED_PREVIEW_GOVERNANCE_UNREADABLE);
        assertThat(acceptedPreview.getPreviewStatus()).isEqualTo(PlanningPreviewStatus.INVALIDATED);
        assertThat(acceptedPreview.getRepairStatusJson()).containsEntry("reason", "accepted_preview_governance_snapshot_unreadable");
    }

    private ProjectGenerationPreparationEntity preparationEntity() {
        return ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(),
                null,
                null,
                GenerationSourceChannel.WEB.name(),
                "Acme Platform",
                "cursor",
                "Build an orchestration workspace",
                Map.of("projectName", "Acme Platform", "summary", "Build an orchestration workspace"),
                Map.of("recommendedStarter", Map.of("key", "nextjs-saas-shell")),
                "1.0",
                Map.of());
    }

    private StarterSelection starterSelection() {
        return new StarterSelection(
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
    }

    private StructuredAnalysis structuredAnalysis() {
        return new StructuredAnalysis(
                "Acme Platform",
                "saas",
                List.of("nextjs", "postgres"),
                List.of("auth", "billing"),
                "complex",
                false,
                "",
                "Build an orchestration workspace");
    }

    private DeterministicPlanningProfile planningProfile() {
        return new DeterministicPlanningProfile(
                PlanningComplexity.COMPLEX,
                List.of(),
                new PlanningShapeEnvelope(2, 4, 3, 6, 2, 6, List.of(), "complex-band", ""),
                List.of());
    }

    private DeterministicPlanningProfile expandedPlanningProfile() {
        return new DeterministicPlanningProfile(
                PlanningComplexity.COMPLEX,
                List.of(),
                new PlanningShapeEnvelope(4, 8, 6, 12, 4, 12, List.of(), "enterprise-band", ""),
                List.of());
    }

    private PlanningModelPolicy planningPolicy(final PlanningMode planningMode, final String degradeStrategy) {
        return new PlanningModelPolicy(
                planningMode,
                "elevated",
                true,
                true,
                "google:gemini-3-pro",
                ModelCapability.HIGH,
                false,
                degradeStrategy);
    }

    private void assertLifecycleViolation(final Runnable action, final LifecycleViolationReason expectedReason) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(LifecycleViolationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(expectedReason));
    }

    private void writePreviewField(final ProjectPlanningPreviewEntity entity, final String fieldName, final Object value) {
        try {
            final var field = ProjectPlanningPreviewEntity.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(entity, value);
        } catch (final Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
