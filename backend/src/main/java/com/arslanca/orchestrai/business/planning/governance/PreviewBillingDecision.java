package com.arslanca.orchestrai.business.planning.governance;

public record PreviewBillingDecision(
        String previewGenerationDisposition,
        String previewCacheHitDisposition,
        String acceptedPreviewStartDisposition,
        String freshStartDisposition,
        boolean previewBilled) {

    public PreviewBillingDecision {
        previewGenerationDisposition = normalize(previewGenerationDisposition, "preview_generated_not_billed");
        previewCacheHitDisposition = normalize(previewCacheHitDisposition, "preview_cache_hit_not_billed");
        acceptedPreviewStartDisposition = normalize(
                acceptedPreviewStartDisposition,
                "accepted_preview_reused_generation_billed_once");
        freshStartDisposition = normalize(freshStartDisposition, "fresh_planning_generation_billed_once");
    }

    private static String normalize(final String value, final String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
