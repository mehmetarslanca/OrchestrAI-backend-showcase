package com.arslanca.orchestrai.business.planning.lifecycle;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.StarterSelectionResolver;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PreviewReuseDisposition;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.planning.blueprint.PreviewPayloadCodec;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

@Service
public class StartAuthorizationPolicy {

    private final PreviewOwnershipValidator previewOwnershipValidator;
    private final StarterSelectionResolver starterSelectionResolver;
    private final PreviewReusePolicy previewReusePolicy;
    private final PreviewPayloadCodec previewPayloadCodec;

    public StartAuthorizationPolicy(
            final PreviewOwnershipValidator previewOwnershipValidator,
            final StarterSelectionResolver starterSelectionResolver,
            final PreviewReusePolicy previewReusePolicy,
            final PreviewPayloadCodec previewPayloadCodec) {
        this.previewOwnershipValidator = previewOwnershipValidator;
        this.starterSelectionResolver = starterSelectionResolver;
        this.previewReusePolicy = previewReusePolicy;
        this.previewPayloadCodec = previewPayloadCodec;
    }

    public StartAuthorization authorize(
            final ProjectGenerationPreparationEntity preparation,
            final GenerationStartRequest request,
            final StructuredAnalysis structuredAnalysis,
            final DeterministicPlanningProfile planningProfile,
            final StarterSelection originalStarterSelection,
            final PlanningModelPolicy planningModelPolicy) {
        final boolean acceptedPreviewRequired = planningModelPolicy.previewRequired()
                || planningProfile.effectiveComplexity().requiresPreviewReview();
        final ProjectPlanningPreviewEntity acceptedPreview = resolveAcceptedPreview(
                preparation,
                request.previewId(),
                acceptedPreviewRequired);

        final StarterSelection selectedStarterSelection = resolveSelectedStarter(
                originalStarterSelection,
                request.starterKey(),
                acceptedPreview);
        previewReusePolicy.ensureAcceptedPreviewReusableForStart(
                preparation,
                acceptedPreview,
                structuredAnalysis,
                planningProfile,
                selectedStarterSelection,
                planningModelPolicy);
        final CanonicalBlueprint acceptedBlueprintPlan = resolveAcceptedBlueprint(acceptedPreview);

        return new StartAuthorization(acceptedPreview, selectedStarterSelection, acceptedBlueprintPlan);
    }

    private ProjectPlanningPreviewEntity resolveAcceptedPreview(
            final ProjectGenerationPreparationEntity preparation,
            final java.util.UUID previewId,
            final boolean acceptedPreviewRequired) {
        final ProjectPlanningPreviewEntity preview = previewOwnershipValidator.loadBoundPreview(preparation, previewId);
        if (preview == null) {
            if (acceptedPreviewRequired) {
                throw new LifecycleViolationException(
                        LifecycleViolationReason.ACCEPTED_PREVIEW_REQUIRED,
                        "This preparation requires an accepted planning preview before generation can start.");
            }
            return null;
        }
        if (preview.getPreviewStatus() == PlanningPreviewStatus.INVALIDATED) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.ACCEPTED_PREVIEW_INVALIDATED,
                    "Accepted planning preview was invalidated and cannot be reused. Please generate a new preview.");
        }
        if (preview.getPreviewStatus() != PlanningPreviewStatus.ACCEPTED) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREVIEW_NOT_ACCEPTED,
                    "Planning preview must be accepted before generation can start.");
        }
        return preview;
    }

    private StarterSelection resolveSelectedStarter(
            final StarterSelection originalSelection,
            final String requestedStarterKey,
            final ProjectPlanningPreviewEntity acceptedPreview) {
        if (acceptedPreview == null) {
            return starterSelectionResolver.resolveSelectedStarter(originalSelection, requestedStarterKey);
        }

        final String previewStarterKey = acceptedPreview.getSelectedStarterKey();
        if (requestedStarterKey != null && !requestedStarterKey.isBlank()
                && (previewStarterKey == null || !previewStarterKey.equalsIgnoreCase(requestedStarterKey.trim()))) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREVIEW_STARTER_MISMATCH,
                    "starterKey does not match the accepted planning preview.");
        }
        if (previewReusePolicy.dispositionFor(previewStarterKey) == PreviewReuseDisposition.REUSE_PREPARATION_SELECTION) {
            return originalSelection;
        }
        return starterSelectionResolver.resolveSelectedStarter(originalSelection, previewStarterKey);
    }

    private CanonicalBlueprint resolveAcceptedBlueprint(final ProjectPlanningPreviewEntity acceptedPreview) {
        if (acceptedPreview == null) {
            return null;
        }
        if (acceptedPreview.getBlueprintPayloadJson().isEmpty()) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.ACCEPTED_PREVIEW_MISSING_PAYLOAD,
                    "Accepted planning preview does not contain a reusable blueprint payload.");
        }
        try {
            return previewPayloadCodec.decodeCanonical(acceptedPreview.getBlueprintPayloadJson());
        } catch (final RuntimeException exception) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.ACCEPTED_PREVIEW_PAYLOAD_UNREADABLE,
                    "Accepted planning preview payload is unreadable. Please generate a new preview.");
        }
    }
}
