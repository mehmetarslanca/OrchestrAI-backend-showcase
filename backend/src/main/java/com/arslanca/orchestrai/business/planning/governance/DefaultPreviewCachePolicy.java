package com.arslanca.orchestrai.business.planning.governance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.dtos.orchestration.PlannerModelRef;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class DefaultPreviewCachePolicy implements PreviewCachePolicy {

    public static final String POLICY_VERSION = "cache-v3";
    private static final String PREVIEW_CACHE_KEY_VERSION = "3.0";
    private final ObjectMapper objectMapper;
    private final PolicyVersionFingerprintResolver fingerprintResolver;
    private final PlanningGovernanceProperties properties;

    public DefaultPreviewCachePolicy(
            final ObjectMapper objectMapper,
            final PolicyVersionFingerprintResolver fingerprintResolver,
            final PlanningGovernanceProperties properties) {
        this.objectMapper = objectMapper;
        this.fingerprintResolver = fingerprintResolver;
        this.properties = properties;
    }

    @Override
    public PreviewCacheDecision buildDecision(final PlanningGovernanceContext context) {
        final String governancePolicyFingerprint = fingerprintResolver.resolve(context).value();
        final String plannerModelFingerprint = context == null || context.planningModelPolicy() == null
                ? PlannerModelRef.unknown().stableValue()
                : context.planningModelPolicy().plannerModelRef().stableValue();
        return new PreviewCacheDecision(
                buildPreviewCacheKey(context),
                plannerModelFingerprint,
                governancePolicyFingerprint,
                properties.getReviewReadyReuseTtlSeconds(),
                properties.getAcceptedPreviewReuseTtlSeconds(),
                true);
    }

    @Override
    public String buildPreviewCacheKey(final PlanningGovernanceContext context) {
        if (context == null) {
            throw new IllegalArgumentException("Planning governance context is required");
        }
        if (context.resolvedIdeTarget() == null || context.resolvedIdeTarget().isBlank()) {
            throw new IllegalArgumentException("Planning governance context must resolve an IDE target");
        }
        final Map<String, Object> cacheSeed = new LinkedHashMap<>();
        cacheSeed.put(
                "normalizedRequirementPacket",
                context.structuredAnalysis() == null
                        ? context.preparation() == null ? null : context.preparation().snapshotStructuredAnalysis()
                        : context.structuredAnalysis());
        cacheSeed.put("effectiveComplexity", context.effectiveComplexity().name());
        cacheSeed.put("shapeEnvelope", context.shapeEnvelope());
        cacheSeed.put("starterKey", context.selectedStarterKey());
        cacheSeed.put("ideTarget", context.resolvedIdeTarget());
        cacheSeed.put(
                "planningMode",
                context.planningModelPolicy() == null
                        ? null
                        : context.planningModelPolicy().selectedPlanningMode().name());
        cacheSeed.put(
                "degradeStrategy",
                context.planningModelPolicy() == null
                        ? null
                        : context.planningModelPolicy().degradeStrategy().value());
        cacheSeed.put(
                "plannerModelFingerprint",
                context.planningModelPolicy() == null
                        ? PlannerModelRef.unknown().stableValue()
                        : context.planningModelPolicy().plannerModelRef().stableValue());
        cacheSeed.put(
                "planningPolicyFingerprint",
                context.planningModelPolicy() == null
                        ? "unknown"
                        : context.planningModelPolicy().policyVersionFingerprint().value());
        cacheSeed.put("governancePolicyFingerprint", fingerprintResolver.resolve(context).value());
        cacheSeed.put("preparationContractVersion", context.preparationContractVersion());
        cacheSeed.put("previewContractVersion", context.previewContractVersion());
        cacheSeed.put("previewCacheKeyVersion", PREVIEW_CACHE_KEY_VERSION);
        try {
            final String payload = objectMapper.writeValueAsString(cacheSeed);
            final MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (final JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("Failed to compute planning preview cache key", exception);
        }
    }

    @Override
    public String policyVersion() {
        return POLICY_VERSION;
    }
}
