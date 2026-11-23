package com.arslanca.orchestrai.business.planning.lifecycle;

public enum LifecycleViolationReason {
    PREPARATION_ALREADY_STARTED("preparation_already_started"),
    PREPARATION_INVALIDATED("preparation_invalidated"),
    CHAT_CHANGED("chat_changed"),
    PREVIEW_GENERATION_IN_PROGRESS("preview_generation_in_progress"),
    PREVIEW_NOT_READY_FOR_ACCEPTANCE("preview_not_ready_for_acceptance"),
    PREVIEW_NOT_ACCEPTED("preview_not_accepted"),
    PREVIEW_EXPIRED("preview_expired"),
    ACCEPTED_PREVIEW_REQUIRED("accepted_preview_required"),
    ACCEPTED_PREVIEW_INVALIDATED("accepted_preview_invalidated"),
    ACCEPTED_PREVIEW_POLICY_DRIFT("accepted_preview_policy_drift"),
    ACCEPTED_PREVIEW_FINGERPRINT_MISMATCH("accepted_preview_fingerprint_mismatch"),
    ACCEPTED_PREVIEW_GOVERNANCE_MISSING("accepted_preview_governance_missing"),
    ACCEPTED_PREVIEW_GOVERNANCE_UNREADABLE("accepted_preview_governance_unreadable"),
    STRONGER_MODEL_REQUIRED("stronger_model_required"),
    PREVIEW_STARTER_MISMATCH("preview_starter_mismatch"),
    ACCEPTED_PREVIEW_MISSING_PAYLOAD("accepted_preview_missing_payload"),
    ACCEPTED_PREVIEW_PAYLOAD_UNREADABLE("accepted_preview_payload_unreadable"),
    LEGACY_PREVIEW_REVIEW_REQUIRED("legacy_preview_review_required"),
    LEGACY_PREVIEW_NOT_READY("legacy_preview_not_ready");

    private final String metricTag;

    LifecycleViolationReason(final String metricTag) {
        this.metricTag = metricTag;
    }

    public String metricTag() {
        return metricTag;
    }
}
