package com.arslanca.orchestrai.business.planning.lifecycle;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PreviewReuseDisposition;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationPreparationResponse;
import com.arslanca.orchestrai.business.dtos.projectgeneration.GenerationStartRequest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

@Service
public class PlanningLifecycleAuthority {

    private final PreparationStateGuard preparationStateGuard;
    private final ChatFreshnessPolicy chatFreshnessPolicy;
    private final PreviewReusePolicy previewReusePolicy;
    private final PreviewOwnershipValidator previewOwnershipValidator;
    private final StartAuthorizationPolicy startAuthorizationPolicy;
    private final LegacyBridgePolicy legacyBridgePolicy;

    public PlanningLifecycleAuthority(
            final PreparationStateGuard preparationStateGuard,
            final ChatFreshnessPolicy chatFreshnessPolicy,
            final PreviewReusePolicy previewReusePolicy,
            final PreviewOwnershipValidator previewOwnershipValidator,
            final StartAuthorizationPolicy startAuthorizationPolicy,
            final LegacyBridgePolicy legacyBridgePolicy) {
        this.preparationStateGuard = preparationStateGuard;
        this.chatFreshnessPolicy = chatFreshnessPolicy;
        this.previewReusePolicy = previewReusePolicy;
        this.previewOwnershipValidator = previewOwnershipValidator;
        this.startAuthorizationPolicy = startAuthorizationPolicy;
        this.legacyBridgePolicy = legacyBridgePolicy;
    }

    public void ensurePreviewGenerationAllowed(final ProjectGenerationPreparationEntity preparation) {
        preparationStateGuard.ensurePreviewGenerationAllowed(preparation);
        chatFreshnessPolicy.ensurePreparationCurrent(preparation);
    }

    public PreviewClaim claimPreview(
            final ProjectGenerationPreparationEntity preparation,
            final StructuredAnalysis structuredAnalysis,
            final DeterministicPlanningProfile planningProfile,
            final StarterSelection selectedStarterSelection,
            final PlanningModelPolicy planningModelPolicy) {
        return previewReusePolicy.claimPreview(
                preparation,
                structuredAnalysis,
                planningProfile,
                selectedStarterSelection,
                planningModelPolicy);
    }

    public ProjectPlanningPreviewEntity loadOwnedPreview(final UUID userId, final UUID previewId) {
        return previewOwnershipValidator.loadOwnedPreview(userId, previewId);
    }

    public void ensurePreviewAcceptanceAllowed(
            final ProjectGenerationPreparationEntity preparation,
            final ProjectPlanningPreviewEntity preview) {
        preparationStateGuard.ensurePreviewAcceptanceAllowed(preparation, preview);
        chatFreshnessPolicy.ensurePreparationCurrent(preparation);
    }

    public void invalidateSupersededSiblingsOnAcceptance(
            final UUID preparationId,
            final String previewCacheKey,
            final UUID keepPreviewId) {
        previewReusePolicy.invalidateSupersededSiblingsOnAcceptance(preparationId, previewCacheKey, keepPreviewId);
    }

    public void ensureStartAllowed(final ProjectGenerationPreparationEntity preparation) {
        preparationStateGuard.ensureStartAllowed(preparation);
        chatFreshnessPolicy.ensurePreparationCurrent(preparation);
        previewReusePolicy.ensureNoActiveDraftPreview(preparation.getId());
    }

    public StartAuthorization authorizeStart(
            final ProjectGenerationPreparationEntity preparation,
            final GenerationStartRequest request,
            final StructuredAnalysis structuredAnalysis,
            final DeterministicPlanningProfile planningProfile,
            final StarterSelection originalStarterSelection,
            final PlanningModelPolicy planningModelPolicy) {
        return startAuthorizationPolicy.authorize(
                preparation,
                request,
                structuredAnalysis,
                planningProfile,
                originalStarterSelection,
                planningModelPolicy);
    }

    public String selectedStarterKeyFor(final StarterSelection starterSelection) {
        return previewReusePolicy.selectedStarterKeyFor(starterSelection);
    }

    public PreviewReuseDisposition dispositionFor(final String selectedStarterKey) {
        return previewReusePolicy.dispositionFor(selectedStarterKey);
    }

    public boolean requiresAcceptedPreview(final GenerationPreparationResponse preparationResponse) {
        return legacyBridgePolicy.requiresAcceptedPreview(preparationResponse);
    }

    public boolean previewRequiresAcceptance(final PlanningPreview preview) {
        return legacyBridgePolicy.previewRequiresAcceptance(preview);
    }
}
