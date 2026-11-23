package com.arslanca.orchestrai.business.planning.governance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;

class DefaultPolicyVersionFingerprintResolverTest {

    @Test
    void shouldReusePlanningModelFingerprintWhenAvailable() {
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(new PlanningGovernanceProperties());
        final DefaultPolicyVersionFingerprintResolver resolver = new DefaultPolicyVersionFingerprintResolver(
                new DefaultPlanningBudgetPolicy(),
                new DefaultPreviewBillingPolicy(),
                rolloutPolicy);

        final PlanningModelPolicy policy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                "model",
                ModelCapability.HIGH,
                false,
                "none");
        final PlanningGovernanceContext context = new PlanningGovernanceContext(preparation(), structuredAnalysis(), "starter", policy, "3.0");

        assertThat(resolver.resolve(context).value())
                .contains(policy.policyVersionFingerprint().value())
                .contains("budget-v2")
                .contains("billing-v2")
                .contains(DefaultPreviewCachePolicy.POLICY_VERSION);
    }

    @Test
    void shouldFallbackToGovernanceVersionsWhenPlanningPolicyMissing() {
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(new PlanningGovernanceProperties());
        final DefaultPolicyVersionFingerprintResolver resolver = new DefaultPolicyVersionFingerprintResolver(
                new DefaultPlanningBudgetPolicy(),
                new DefaultPreviewBillingPolicy(),
                rolloutPolicy);

        final PlanningGovernanceContext context = new PlanningGovernanceContext(preparation(), structuredAnalysis(), "starter", null, "3.0");

        assertThat(resolver.resolve(context).value())
                .contains("budget-v2")
                .contains("billing-v2")
                .contains(DefaultPreviewCachePolicy.POLICY_VERSION)
                .contains("rollout-v1");
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

    private ProjectGenerationPreparationEntity preparation() {
        return ProjectGenerationPreparationEntity.create(
                UUID.randomUUID(), null, null, "WEB", "project", "cursor", "desc", Map.of(), Map.of(), "1.0", Map.of());
    }
}
