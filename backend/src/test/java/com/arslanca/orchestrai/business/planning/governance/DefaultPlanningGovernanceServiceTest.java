package com.arslanca.orchestrai.business.planning.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.fasterxml.jackson.databind.ObjectMapper;

class DefaultPlanningGovernanceServiceTest {

    @Test
    void shouldCreateDeterministicGovernanceSnapshotAndEmitTelemetry() {
        final PlanningTelemetryService telemetryService = mock(PlanningTelemetryService.class);
        final PlanningGovernanceProperties properties = properties();
        final PlanningRolloutPolicy rolloutPolicy = new PropertyBackedPlanningRolloutPolicy(properties);
        final DefaultPolicyVersionFingerprintResolver resolver = new DefaultPolicyVersionFingerprintResolver(
                new DefaultPlanningBudgetPolicy(),
                new DefaultPreviewBillingPolicy(),
                rolloutPolicy);
        final DefaultPlanningGovernanceService service = new DefaultPlanningGovernanceService(
                new DefaultPlanningBudgetPolicy(),
                new DefaultPreviewBillingPolicy(),
                new DefaultPreviewCachePolicy(new ObjectMapper(), resolver, properties),
                telemetryService,
                rolloutPolicy,
                resolver);

        final PlanningModelPolicy policy = new PlanningModelPolicy(
                PlanningMode.MULTI_PASS_EXPANSION,
                "elevated",
                true,
                true,
                "model",
                ModelCapability.HIGH,
                false,
                "none");
        final PlanningGovernanceContext context = new PlanningGovernanceContext(
                preparation(), structuredAnalysis(), "starter", policy, "3.0");
        final PlanningGovernanceSnapshot snapshot = service.createSnapshot(context);

        assertThat(snapshot.budgetDecision().allowed()).isTrue();
        assertThat(snapshot.billingDecision().previewGenerationDisposition()).isEqualTo("preview_generated_not_billed");
        assertThat(snapshot.cacheDecision().previewCacheKey()).isNotBlank();
        assertThat(snapshot.rolloutDecision().mode()).isEqualTo(PlanningRolloutMode.OFF);
        assertThat(snapshot.effectiveComplexity()).isEqualTo(snapshot.complexity());
        assertThat(snapshot.ideTarget()).isEqualTo("cursor");
        assertThat(snapshot.selectedStarterKey()).isEqualTo("starter");
        assertThat(snapshot.planningPolicyFingerprint().value()).isEqualTo(policy.policyVersionFingerprint().value());
        assertThat(snapshot.governancePolicyFingerprint().value()).contains("budget-v2").contains("billing-v2");
        verify(telemetryService).recordSnapshot(context, snapshot);
    }

    private PlanningGovernanceProperties properties() {
        return new PlanningGovernanceProperties();
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
