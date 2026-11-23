package com.arslanca.orchestrai.business.planning.lifecycle;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PreviewReuseDisposition;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreview;
import com.arslanca.orchestrai.business.dtos.projectgeneration.PlanningPreviewStatus;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceContext;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshot;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceSnapshotCodec;
import com.arslanca.orchestrai.business.planning.governance.PlanningGovernanceService;
import com.arslanca.orchestrai.business.planning.governance.PlanningTelemetryService;
import com.arslanca.orchestrai.dataaccess.abstracts.ProjectPlanningPreviewRepository;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.entity.concretes.ProjectPlanningPreviewEntity;

@Service
public class PreviewReusePolicy {

    private static final Duration STALE_DRAFT_TIMEOUT = Duration.ofMinutes(10);

    private final ProjectPlanningPreviewRepository planningPreviewRepository;
    private final PlanningGovernanceService planningGovernanceService;
    private final PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec;
    private final PlanningTelemetryService planningTelemetryService;

    public PreviewReusePolicy(
            final ProjectPlanningPreviewRepository planningPreviewRepository,
            final PlanningGovernanceService planningGovernanceService,
            final PlanningGovernanceSnapshotCodec planningGovernanceSnapshotCodec,
            final PlanningTelemetryService planningTelemetryService) {
        this.planningPreviewRepository = planningPreviewRepository;
        this.planningGovernanceService = planningGovernanceService;
        this.planningGovernanceSnapshotCodec = planningGovernanceSnapshotCodec;
        this.planningTelemetryService = planningTelemetryService;
    }

    public PreviewReuseDisposition dispositionFor(final String selectedStarterKey) {
        return PreviewReuseDisposition.fromSelectedStarterKey(selectedStarterKey);
    }

    public String selectedStarterKeyFor(final StarterSelection starterSelection) {
        if (starterSelection == null
                || starterSelection.docsOnlyFallback()
                || starterSelection.recommendedStarter() == null) {
            return PreviewReuseDisposition.legacyDocsOnlyStarterKey();
        }
        return starterSelection.recommendedStarter().key();
    }

    public PreviewClaim claimPreview(
            final ProjectGenerationPreparationEntity preparation,
            final StructuredAnalysis structuredAnalysis,
            final DeterministicPlanningProfile planningProfile,
            final StarterSelection selectedStarterSelection,
            final PlanningModelPolicy planningModelPolicy) {
        final List<ProjectPlanningPreviewEntity> previews = planningPreviewRepository
                .findByPreparationIdOrderByCreatedAtDesc(preparation.getId());
        ensureNoActiveDraftPreview(previews);

        final PlanningGovernanceSnapshot governanceSnapshot = planningGovernanceService.createSnapshot(buildContext(
                preparation,
                structuredAnalysis,
                planningProfile,
                selectedStarterSelection,
                planningModelPolicy));
        if (!governanceSnapshot.budgetDecision().allowed()) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.STRONGER_MODEL_REQUIRED,
                    governanceSnapshot.budgetDecision().reason().isBlank()
                            ? "Planning governance rejected this preview request."
                            : "Planning governance rejected this preview request: "
                                    + governanceSnapshot.budgetDecision().reason());
        }

        final ReuseScanResult reuseScanResult = scanReusablePreview(
                previews,
                governanceSnapshot.cacheDecision().previewCacheKey());
        final ProjectPlanningPreviewEntity reusablePreview = reuseScanResult.reusablePreview();
        if (reusablePreview != null) {
            planningTelemetryService.recordPreviewCacheOutcome(
                    planningGovernanceSnapshotCodec.readSnapshot(reusablePreview.getGovernanceSnapshotJson()),
                    "hit");
            return new PreviewClaim(reusablePreview, true);
        }

        final ProjectPlanningPreviewEntity draftPreview = ProjectPlanningPreviewEntity.createDraft(
                preparation.getId(),
                selectedStarterKeyFor(selectedStarterSelection),
                planningModelPolicy.selectedPlanningMode(),
                governanceSnapshot.cacheDecision().previewCacheKey(),
                PlanningPreview.CURRENT_CONTRACT_VERSION);

        draftPreview.attachGovernanceSnapshot(planningGovernanceSnapshotCodec.toPayload(governanceSnapshot));
        planningTelemetryService.recordPreviewCacheOutcome(governanceSnapshot, reuseScanResult.outcome());

        final ProjectPlanningPreviewEntity savedDraft = planningPreviewRepository.save(draftPreview);
        return new PreviewClaim(savedDraft, false);
    }

    public void ensureAcceptedPreviewReusableForStart(
            final ProjectGenerationPreparationEntity preparation,
            final ProjectPlanningPreviewEntity preview,
            final StructuredAnalysis structuredAnalysis,
            final DeterministicPlanningProfile planningProfile,
            final StarterSelection selectedStarterSelection,
            final PlanningModelPolicy planningModelPolicy) {
        if (preview == null || preview.getPreviewStatus() != PlanningPreviewStatus.ACCEPTED) {
            return;
        }
        if (preview.getGovernanceSnapshotJson().isEmpty()) {
            invalidateAndSave(preview, "accepted_preview_governance_snapshot_missing", null);
            throw new LifecycleViolationException(
                    LifecycleViolationReason.ACCEPTED_PREVIEW_GOVERNANCE_MISSING,
                    "Accepted planning preview is missing governance metadata. Please generate a new preview.");
        }
        final PlanningGovernanceSnapshot acceptedSnapshot;
        try {
            acceptedSnapshot = planningGovernanceSnapshotCodec.readSnapshot(preview.getGovernanceSnapshotJson());
        } catch (final RuntimeException exception) {
            invalidateAndSave(preview, "accepted_preview_governance_snapshot_unreadable", null);
            throw new LifecycleViolationException(
                    LifecycleViolationReason.ACCEPTED_PREVIEW_GOVERNANCE_UNREADABLE,
                    "Accepted planning preview governance metadata is unreadable. Please generate a new preview.");
        }
        if (isExpired(preview, acceptedSnapshot, true)) {
            invalidateAndSave(preview, "accepted_preview_expired", acceptedSnapshot);
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREVIEW_EXPIRED,
                    "Accepted planning preview expired. Please generate a new preview.");
        }

        final PlanningGovernanceSnapshot currentSnapshot = planningGovernanceService.createSnapshot(buildContext(
                preparation,
                structuredAnalysis,
                planningProfile,
                selectedStarterSelection,
                planningModelPolicy));

        if (hasPolicyDrift(acceptedSnapshot, currentSnapshot)) {
            invalidateAndSave(preview, "accepted_preview_policy_drift", acceptedSnapshot);
            throw new LifecycleViolationException(
                    LifecycleViolationReason.ACCEPTED_PREVIEW_POLICY_DRIFT,
                    "Accepted planning preview no longer matches current planning policy. Please generate a new preview.");
        }

        if (!hasCompatibleGovernanceFingerprint(preview, acceptedSnapshot, currentSnapshot)) {
            invalidateAndSave(preview, "accepted_preview_fingerprint_mismatch", acceptedSnapshot);
            throw new LifecycleViolationException(
                    LifecycleViolationReason.ACCEPTED_PREVIEW_FINGERPRINT_MISMATCH,
                    "Accepted planning preview no longer matches the current request shape. Please generate a new preview.");
        }
    }

    public void ensureNoActiveDraftPreview(final UUID preparationId) {
        ensureNoActiveDraftPreview(planningPreviewRepository.findByPreparationIdOrderByCreatedAtDesc(preparationId));
    }

    public void invalidateSupersededSiblingsOnAcceptance(
            final UUID preparationId,
            final String previewCacheKey,
            final UUID keepPreviewId) {
        final List<ProjectPlanningPreviewEntity> previewsToInvalidate = planningPreviewRepository
                .findByPreparationIdOrderByCreatedAtDesc(preparationId)
                .stream()
                .filter(preview -> keepPreviewId == null || !preview.getId().equals(keepPreviewId))
                .filter(preview -> !previewCacheKey.equals(preview.getPreviewCacheKey()))
                .filter(this::isSupersedeCandidate)
                .toList();

        previewsToInvalidate.forEach(preview -> invalidatePreview(
                preview,
                "superseded_by_accepted_preview",
                readSnapshotIfPossible(preview)));
        if (!previewsToInvalidate.isEmpty()) {
            planningPreviewRepository.saveAll(previewsToInvalidate);
        }
    }

    private void ensureNoActiveDraftPreview(final List<ProjectPlanningPreviewEntity> previews) {
        invalidateStaleDraftPreviews(previews);

        final boolean hasDraftPreview = previews.stream()
                .anyMatch(preview -> preview.getPreviewStatus() == PlanningPreviewStatus.DRAFT);
        if (hasDraftPreview) {
            throw new LifecycleViolationException(
                    LifecycleViolationReason.PREVIEW_GENERATION_IN_PROGRESS,
                    "A planning preview for this preparation is currently being generated. Please wait.");
        }
    }

    private void invalidateStaleDraftPreviews(final List<ProjectPlanningPreviewEntity> previews) {
        final OffsetDateTime staleThreshold = OffsetDateTime.now().minus(STALE_DRAFT_TIMEOUT);
        final List<ProjectPlanningPreviewEntity> staleDrafts = previews.stream()
                .filter(preview -> preview.getPreviewStatus() == PlanningPreviewStatus.DRAFT)
                .filter(preview -> preview.getCreatedAt() != null)
                .filter(preview -> preview.getCreatedAt().isBefore(staleThreshold))
                .toList();

        staleDrafts.forEach(preview -> invalidatePreview(
                preview,
                "stale_draft_recovered",
                readSnapshotIfPossible(preview)));
        if (!staleDrafts.isEmpty()) {
            planningPreviewRepository.saveAll(staleDrafts);
        }
    }

    private boolean isSupersedeCandidate(final ProjectPlanningPreviewEntity preview) {
        return switch (preview.getPreviewStatus()) {
            case DRAFT, READY_FOR_REVIEW, ACCEPTED -> true;
            default -> false;
        };
    }

    private PlanningGovernanceContext buildContext(
            final ProjectGenerationPreparationEntity preparation,
            final StructuredAnalysis structuredAnalysis,
            final DeterministicPlanningProfile planningProfile,
            final StarterSelection selectedStarterSelection,
            final PlanningModelPolicy planningModelPolicy) {
        return new PlanningGovernanceContext(
                preparation,
                structuredAnalysis,
                planningProfile,
                selectedStarterKeyFor(selectedStarterSelection),
                preparation == null ? null : preparation.getIdeTarget(),
                planningModelPolicy,
                PlanningPreview.CURRENT_CONTRACT_VERSION);
    }

    private ReuseScanResult scanReusablePreview(
            final List<ProjectPlanningPreviewEntity> previews,
            final String previewCacheKey) {
        final List<ProjectPlanningPreviewEntity> expiredPreviews = new ArrayList<>();
        String outcome = "miss";
        for (final ProjectPlanningPreviewEntity preview : previews) {
            if (!previewCacheKey.equals(preview.getPreviewCacheKey())) {
                continue;
            }
            if (preview.getPreviewStatus() != PlanningPreviewStatus.READY_FOR_REVIEW
                    && preview.getPreviewStatus() != PlanningPreviewStatus.ACCEPTED) {
                continue;
            }
            if (preview.getGovernanceSnapshotJson().isEmpty()) {
                outcome = "legacy_ineligible";
                continue;
            }
            final PlanningGovernanceSnapshot snapshot;
            try {
                snapshot = planningGovernanceSnapshotCodec.readSnapshot(preview.getGovernanceSnapshotJson());
            } catch (final RuntimeException exception) {
                invalidatePreview(preview, "preview_governance_snapshot_unreadable", null);
                expiredPreviews.add(preview);
                outcome = "governance_unreadable";
                continue;
            }
            if (!snapshot.cacheDecision().cacheable()) {
                outcome = "cache_disabled";
                continue;
            }
            final boolean acceptedPreview = preview.getPreviewStatus() == PlanningPreviewStatus.ACCEPTED;
            if (isExpired(preview, snapshot, acceptedPreview)) {
                invalidatePreview(preview, "governance_reuse_ttl_expired", snapshot);
                expiredPreviews.add(preview);
                outcome = "expired";
                continue;
            }
            return new ReuseScanResult(preview, "hit");
        }
        if (!expiredPreviews.isEmpty()) {
            planningPreviewRepository.saveAll(expiredPreviews);
        }
        return new ReuseScanResult(null, outcome);
    }

    private boolean isExpired(
            final ProjectPlanningPreviewEntity preview,
            final PlanningGovernanceSnapshot snapshot,
            final boolean acceptedPreview) {
        final long ttlSeconds = acceptedPreview
                ? snapshot.cacheDecision().acceptedPreviewReuseTtlSeconds()
                : snapshot.cacheDecision().reviewReadyReuseTtlSeconds();
        if (ttlSeconds <= 0L) {
            return true;
        }
        final OffsetDateTime createdAt = preview.getCreatedAt();
        return createdAt != null && createdAt.isBefore(OffsetDateTime.now().minusSeconds(ttlSeconds));
    }

    private boolean hasPolicyDrift(
            final PlanningGovernanceSnapshot acceptedSnapshot,
            final PlanningGovernanceSnapshot currentSnapshot) {
        return !Objects.equals(
                acceptedSnapshot.planningPolicyFingerprint().value(),
                currentSnapshot.planningPolicyFingerprint().value())
                || !Objects.equals(
                        acceptedSnapshot.governancePolicyFingerprint().value(),
                        currentSnapshot.governancePolicyFingerprint().value())
                || !Objects.equals(
                        acceptedSnapshot.cacheDecision().governancePolicyFingerprint(),
                        currentSnapshot.cacheDecision().governancePolicyFingerprint());
    }

    private boolean hasCompatibleGovernanceFingerprint(
            final ProjectPlanningPreviewEntity preview,
            final PlanningGovernanceSnapshot acceptedSnapshot,
            final PlanningGovernanceSnapshot currentSnapshot) {
        final String acceptedCacheKey = acceptedSnapshot.cacheDecision().previewCacheKey();
        final String currentCacheKey = currentSnapshot.cacheDecision().previewCacheKey();
        if (acceptedCacheKey == null || acceptedCacheKey.isBlank() || currentCacheKey == null || currentCacheKey.isBlank()) {
            return false;
        }
        return Objects.equals(preview.getPreviewCacheKey(), currentCacheKey)
                && Objects.equals(acceptedCacheKey, currentCacheKey);
    }

    private void invalidateAndSave(
            final ProjectPlanningPreviewEntity preview,
            final String reason,
            final PlanningGovernanceSnapshot snapshot) {
        invalidatePreview(preview, reason, snapshot);
        planningPreviewRepository.save(preview);
    }

    private void invalidatePreview(
            final ProjectPlanningPreviewEntity preview,
            final String reason,
            final PlanningGovernanceSnapshot snapshot) {
        final PlanningPreviewStatus previousStatus = preview.getPreviewStatus();
        preview.markInvalidated(reason);
        planningTelemetryService.recordPreviewInvalidation(snapshot, previousStatus, reason);
    }

    private PlanningGovernanceSnapshot readSnapshotIfPossible(final ProjectPlanningPreviewEntity preview) {
        if (preview == null || preview.getGovernanceSnapshotJson().isEmpty()) {
            return null;
        }
        try {
            return planningGovernanceSnapshotCodec.readSnapshot(preview.getGovernanceSnapshotJson());
        } catch (final RuntimeException exception) {
            return null;
        }
    }

    private record ReuseScanResult(ProjectPlanningPreviewEntity reusablePreview, String outcome) {
    }
}
