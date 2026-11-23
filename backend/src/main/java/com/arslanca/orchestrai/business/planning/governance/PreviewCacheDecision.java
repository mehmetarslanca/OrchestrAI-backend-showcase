package com.arslanca.orchestrai.business.planning.governance;

public record PreviewCacheDecision(
        String previewCacheKey,
        String plannerModelFingerprint,
        String governancePolicyFingerprint,
        long reviewReadyReuseTtlSeconds,
        long acceptedPreviewReuseTtlSeconds,
        boolean cacheable) {

    public PreviewCacheDecision {
        previewCacheKey = previewCacheKey == null ? "" : previewCacheKey.trim();
        plannerModelFingerprint = plannerModelFingerprint == null || plannerModelFingerprint.isBlank()
                ? "unknown"
                : plannerModelFingerprint.trim();
        governancePolicyFingerprint = governancePolicyFingerprint == null || governancePolicyFingerprint.isBlank()
                ? "unknown"
                : governancePolicyFingerprint.trim();
        reviewReadyReuseTtlSeconds = Math.max(0L, reviewReadyReuseTtlSeconds);
        acceptedPreviewReuseTtlSeconds = Math.max(0L, acceptedPreviewReuseTtlSeconds);
    }
}
