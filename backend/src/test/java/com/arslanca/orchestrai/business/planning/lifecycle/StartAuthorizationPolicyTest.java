package com.arslanca.orchestrai.business.planning.lifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.arslanca.orchestrai.business.abstracts.StarterSelectionResolver;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.PreviewReuseDisposition;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationSourceChannel;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.blueprint.PreviewPayloadCodec;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

@ExtendWith(MockitoExtension.class)
class StartAuthorizationPolicyTest {

    @Mock
    private PreviewOwnershipValidator previewOwnershipValidator;
    @Mock
    private StarterSelectionResolver starterSelectionResolver;
    @Mock
    private PreviewReusePolicy previewReusePolicy;
    @Mock
    private PreviewPayloadCodec previewPayloadCodec;

    private StartAuthorizationPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new StartAuthorizationPolicy(
                previewOwnershipValidator,
                starterSelectionResolver,
                previewReusePolicy,
                previewPayloadCodec);
    }

    @Test
    void shouldRejectComplexStartWithoutAcceptedPreview() {
        final ProjectGenerationPreparationEntity preparation = preparation();
        final StarterSelection starterSelection = starterSelection();
        when(previewOwnershipValidator.loadBoundPreview(preparation, null)).thenReturn(null);

        assertThatThrownBy(() -> policy.authorize(
                preparation,
                new GenerationStartRequest(preparation.getId(), null, null),
                structuredAnalysis(),
                complexProfile(),
                starterSelection,
                planningPolicy(true)))
                .isInstanceOfSatisfying(LifecycleViolationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(LifecycleViolationReason.ACCEPTED_PREVIEW_REQUIRED));
    }

    @Test
    void shouldRejectInvalidatedAcceptedPreviewClearly() {
        final ProjectGenerationPreparationEntity preparation = preparation();
        final ProjectPlanningPreviewEntity preview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                PlanningPreview.CURRENT_CONTRACT_VERSION);
        preview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        preview.markAccepted();
        preview.markInvalidated("accepted_preview_policy_drift");
        when(previewOwnershipValidator.loadBoundPreview(eq(preparation), any())).thenReturn(preview);

        assertThatThrownBy(() -> policy.authorize(
                preparation,
                new GenerationStartRequest(preparation.getId(), null, UUID.randomUUID()),
                structuredAnalysis(),
                complexProfile(),
                starterSelection(),
                planningPolicy(true)))
                .isInstanceOfSatisfying(LifecycleViolationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(LifecycleViolationReason.ACCEPTED_PREVIEW_INVALIDATED));
    }

    @Test
    void shouldRejectUnreadableAcceptedPreviewPayloadClearly() {
        final ProjectGenerationPreparationEntity preparation = preparation();
        final StarterSelection starterSelection = starterSelection();
        final ProjectPlanningPreviewEntity preview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                PlanningPreview.CURRENT_CONTRACT_VERSION);
        preview.markReadyForReview(Map.of(), Map.of(), Map.of("payloadVersion", "999.0"));
        preview.markAccepted();

        when(previewOwnershipValidator.loadBoundPreview(eq(preparation), any())).thenReturn(preview);
        when(previewReusePolicy.dispositionFor("nextjs-saas-shell")).thenReturn(PreviewReuseDisposition.RESOLVE_ACCEPTED_STARTER);
        when(starterSelectionResolver.resolveSelectedStarter(starterSelection, "nextjs-saas-shell")).thenReturn(starterSelection);
        doNothing().when(previewReusePolicy).ensureAcceptedPreviewReusableForStart(
                eq(preparation),
                eq(preview),
                any(),
                any(),
                eq(starterSelection),
                any());
        when(previewPayloadCodec.decodeCanonical(any())).thenThrow(new IllegalStateException("bad payload"));

        assertThatThrownBy(() -> policy.authorize(
                preparation,
                new GenerationStartRequest(preparation.getId(), null, UUID.randomUUID()),
                structuredAnalysis(),
                complexProfile(),
                starterSelection,
                planningPolicy(true)))
                .isInstanceOfSatisfying(LifecycleViolationException.class, exception ->
                        assertThat(exception.reason()).isEqualTo(LifecycleViolationReason.ACCEPTED_PREVIEW_PAYLOAD_UNREADABLE));
    }

    @Test
    void shouldAllowCompatibleAcceptedPreviewReuse() {
        final ProjectGenerationPreparationEntity preparation = preparation();
        final StarterSelection starterSelection = starterSelection();
        final ProjectPlanningPreviewEntity preview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                "nextjs-saas-shell",
                PlanningMode.MULTI_PASS_EXPANSION,
                "cache-key",
                PlanningPreview.CURRENT_CONTRACT_VERSION);
        final CanonicalBlueprint canonicalBlueprint = mock(CanonicalBlueprint.class);
        preview.markReadyForReview(Map.of(), Map.of(), Map.of("projectName", "Acme Platform"));
        preview.markAccepted();

        when(previewOwnershipValidator.loadBoundPreview(eq(preparation), any())).thenReturn(preview);
        when(previewReusePolicy.dispositionFor("nextjs-saas-shell")).thenReturn(PreviewReuseDisposition.RESOLVE_ACCEPTED_STARTER);
        when(starterSelectionResolver.resolveSelectedStarter(starterSelection, "nextjs-saas-shell")).thenReturn(starterSelection);
        doNothing().when(previewReusePolicy).ensureAcceptedPreviewReusableForStart(
                eq(preparation),
                eq(preview),
                any(),
                any(),
                eq(starterSelection),
                any());
        when(previewPayloadCodec.decodeCanonical(any())).thenReturn(canonicalBlueprint);

        final StartAuthorization authorization = policy.authorize(
                preparation,
                new GenerationStartRequest(preparation.getId(), null, UUID.randomUUID()),
                structuredAnalysis(),
                complexProfile(),
                starterSelection,
                planningPolicy(true));

        assertThat(authorization.acceptedPreview()).isEqualTo(preview);
        assertThat(authorization.previewBlueprint()).isEqualTo(canonicalBlueprint);
    }

    private ProjectGenerationPreparationEntity preparation() {
        return ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(),
                null,
                null,
                GenerationSourceChannel.WEB.name(),
                "Acme Platform",
                "cursor",
                "Build an orchestration workspace",
                Map.of("projectName", "Acme Platform"),
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

    private DeterministicPlanningProfile complexProfile() {
        return new DeterministicPlanningProfile(
                PlanningComplexity.COMPLEX,
                List.of(),
                new PlanningShapeEnvelope(2, 4, 3, 6, 2, 6, List.of(), "complex-band", ""),
                List.of());
    }

    private PlanningModelPolicy planningPolicy(final boolean previewRequired) {
        return new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                previewRequired,
                previewRequired,
                "google:gemini-3-pro",
                ModelCapability.HIGH,
                false,
                "none");
    }
}
