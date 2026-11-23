package com.arslanca.orchestrai.business.planning.governance;

import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningShapeEnvelope;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPreviewCachePolicyTest {

    @Test
    void shouldBuildStableCacheKeyForSameContext() {
        final PlanningGovernanceProperties properties = new PlanningGovernanceProperties();
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(properties);
        DefaultPreviewCachePolicy policy = new DefaultPreviewCachePolicy(
                new ObjectMapper(),
                new DefaultPolicyVersionFingerprintResolver(
                        new DefaultPlanningBudgetPolicy(),
                        new DefaultPreviewBillingPolicy(),
                        rolloutPolicy),
                properties);

        ProjectGenerationPreparationEntity entity = ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(), null, null, "WEB", "project", "cursor", "desc", Map.of(), Map.of(), "1.0", Map.of()
        );

        PlanningModelPolicy modelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION, "elevated", true, true, "model", ModelCapability.HIGH, false, "none"
        );

        PlanningGovernanceContext context = new PlanningGovernanceContext(entity, structuredAnalysis(), "starter", modelPolicy, "3.0");

        String firstKey = policy.buildPreviewCacheKey(context);
        String secondKey = policy.buildPreviewCacheKey(context);

        assertThat(firstKey).isEqualTo(secondKey);
        assertThat(firstKey).isNotBlank();
    }

    @Test
    void shouldChangeCacheKeyWhenStarterChanges() {
        final PlanningGovernanceProperties properties = new PlanningGovernanceProperties();
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(properties);
        DefaultPreviewCachePolicy policy = new DefaultPreviewCachePolicy(
                new ObjectMapper(),
                new DefaultPolicyVersionFingerprintResolver(
                        new DefaultPlanningBudgetPolicy(),
                        new DefaultPreviewBillingPolicy(),
                        rolloutPolicy),
                properties);

        ProjectGenerationPreparationEntity entity = ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(), null, null, "WEB", "project", "cursor", "desc", Map.of(), Map.of(), "1.0", Map.of()
        );

        PlanningModelPolicy modelPolicy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION, "elevated", true, true, "model", ModelCapability.HIGH, false, "none"
        );

        String firstKey = policy.buildPreviewCacheKey(new PlanningGovernanceContext(entity, structuredAnalysis(), "starter-a", modelPolicy, "3.0"));
        String secondKey = policy.buildPreviewCacheKey(new PlanningGovernanceContext(entity, structuredAnalysis(), "starter-b", modelPolicy, "3.0"));

        assertThat(firstKey).isNotEqualTo(secondKey);
    }

    @Test
    void shouldChangeCacheKeyWhenEffectiveComplexityShapePlanningModeOrDegradeStrategyChanges() {
        final PlanningGovernanceProperties properties = new PlanningGovernanceProperties();
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(properties);
        final DefaultPreviewCachePolicy policy = new DefaultPreviewCachePolicy(
                new ObjectMapper(),
                new DefaultPolicyVersionFingerprintResolver(
                        new DefaultPlanningBudgetPolicy(),
                        new DefaultPreviewBillingPolicy(),
                        rolloutPolicy),
                properties);

        final ProjectGenerationPreparationEntity entity = ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(), null, null, "WEB", "project", "cursor", "desc", Map.of(), Map.of(), "1.0", Map.of());
        final StructuredAnalysis analysis = structuredAnalysis();
        final String baseKey = policy.buildPreviewCacheKey(new PlanningGovernanceContext(
                entity,
                analysis,
                new DeterministicPlanningProfile(
                        PlanningComplexity.MODERATE,
                        java.util.List.of(),
                        new PlanningShapeEnvelope(2, 4, 3, 6, 2, 6, java.util.List.of(), "moderate-band", ""),
                        java.util.List.of()),
                "starter",
                "cursor",
                new PlanningModelPolicy(
                        PlanningMode.REDUCED_MULTI_PASS,
                        "moderate",
                        false,
                        false,
                        "model",
                        ModelCapability.HIGH,
                        false,
                        "none"),
                "3.0"));

        final String changedComplexityKey = policy.buildPreviewCacheKey(new PlanningGovernanceContext(
                entity,
                analysis,
                new DeterministicPlanningProfile(
                        PlanningComplexity.COMPLEX,
                        java.util.List.of(),
                        new PlanningShapeEnvelope(2, 4, 3, 6, 2, 6, java.util.List.of(), "moderate-band", ""),
                        java.util.List.of()),
                "starter",
                "cursor",
                new PlanningModelPolicy(
                        PlanningMode.REDUCED_MULTI_PASS,
                        "moderate",
                        false,
                        false,
                        "model",
                        ModelCapability.HIGH,
                        false,
                        "none"),
                "3.0"));
        final String changedShapeKey = policy.buildPreviewCacheKey(new PlanningGovernanceContext(
                entity,
                analysis,
                new DeterministicPlanningProfile(
                        PlanningComplexity.MODERATE,
                        java.util.List.of(),
                        new PlanningShapeEnvelope(3, 6, 5, 9, 4, 10, java.util.List.of(), "expanded-band", ""),
                        java.util.List.of()),
                "starter",
                "cursor",
                new PlanningModelPolicy(
                        PlanningMode.REDUCED_MULTI_PASS,
                        "moderate",
                        false,
                        false,
                        "model",
                        ModelCapability.HIGH,
                        false,
                        "none"),
                "3.0"));
        final String changedPlanningModeKey = policy.buildPreviewCacheKey(new PlanningGovernanceContext(
                entity,
                analysis,
                new DeterministicPlanningProfile(
                        PlanningComplexity.MODERATE,
                        java.util.List.of(),
                        new PlanningShapeEnvelope(2, 4, 3, 6, 2, 6, java.util.List.of(), "moderate-band", ""),
                        java.util.List.of()),
                "starter",
                "cursor",
                new PlanningModelPolicy(
                        PlanningMode.MULTI_PASS_EXPANSION,
                        "moderate",
                        false,
                        false,
                        "model",
                        ModelCapability.HIGH,
                        false,
                        "none"),
                "3.0"));
        final String changedDegradeStrategyKey = policy.buildPreviewCacheKey(new PlanningGovernanceContext(
                entity,
                analysis,
                new DeterministicPlanningProfile(
                        PlanningComplexity.MODERATE,
                        java.util.List.of(),
                        new PlanningShapeEnvelope(2, 4, 3, 6, 2, 6, java.util.List.of(), "moderate-band", ""),
                        java.util.List.of()),
                "starter",
                "cursor",
                new PlanningModelPolicy(
                        PlanningMode.REDUCED_MULTI_PASS,
                        "moderate",
                        false,
                        false,
                        "model",
                        ModelCapability.HIGH,
                        false,
                        "stronger_model_required"),
                "3.0"));

        assertThat(changedComplexityKey).isNotEqualTo(baseKey);
        assertThat(changedShapeKey).isNotEqualTo(baseKey);
        assertThat(changedPlanningModeKey).isNotEqualTo(baseKey);
        assertThat(changedDegradeStrategyKey).isNotEqualTo(baseKey);
    }

    private StructuredAnalysis structuredAnalysis() {
        return new StructuredAnalysis(
                "project",
                "saas",
                java.util.List.of("spring"),
                java.util.List.of("auth"),
                "complex",
                false,
                "",
                "summary");
    }
}
