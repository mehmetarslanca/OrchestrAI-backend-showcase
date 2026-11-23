package com.arslanca.orchestrai.business.dtos.orchestration;

/**
 * Result of validating a {@link StructuredAnalysis}.
 *
 * <p>
 * Used internally to determine whether the analysis should
 * proceed to Stage 2 or be rejected.
 * </p>
 */
public record AnalysisValidationResult(
        boolean valid,
        String rejectionReason) {
    public static AnalysisValidationResult accepted() {
        return new AnalysisValidationResult(true, null);
    }

    public static AnalysisValidationResult rejected(final String reason) {
        return new AnalysisValidationResult(false, reason);
    }
}
