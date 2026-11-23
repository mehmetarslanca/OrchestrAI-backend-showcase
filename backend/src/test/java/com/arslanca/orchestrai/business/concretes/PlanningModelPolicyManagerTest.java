package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.CapabilityRisk;
import com.arslanca.orchestrai.business.dtos.orchestration.DegradeStrategy;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;

class PlanningModelPolicyManagerTest {

    private final PlanningModelPolicyManager manager = new PlanningModelPolicyManager();

    @Test
    void shouldResolveEnterpriseWarningForLowCapabilityLocalModel() {
        final ResolvedHeavyModel localLowCapabilityModel = new ResolvedHeavyModel(
                "ollama:deepseek-r1",
                "ollama",
                "deepseek",
                CredentialSource.PLATFORM,
                "platform",
                "deepseek-r1",
                "DeepSeek R1",
                false,
                false,
                null,
                null,
                null,
                java.util.List.of("LOCAL"),
                ModelCapability.LOW);

        final PlanningModelPolicy policy = manager.resolvePolicy(
                PlanningComplexity.ENTERPRISE,
                localLowCapabilityModel,
                false);

        assertThat(policy.selectedPlanningMode()).isEqualTo(PlanningMode.REDUCED_MULTI_PASS);
        assertThat(policy.capabilityRisk()).isEqualTo(CapabilityRisk.CRITICAL);
        assertThat(policy.degradeStrategy()).isEqualTo(DegradeStrategy.ENTERPRISE_QUALITY_WARNING);
        assertThat(policy.blocksPreviewGeneration()).isFalse();
        assertThat(policy.policyVersionFingerprint().value()).contains("enterprise_quality_warning");
    }

    @Test
    void shouldPreserveTolerantFallbackWhenPlannerModelIsUnavailable() {
        final PlanningModelPolicy policy = manager.resolvePolicy(
                PlanningComplexity.COMPLEX,
                null,
                true);

        assertThat(policy.capabilityRisk()).isEqualTo(CapabilityRisk.HIGH);
        assertThat(policy.degradeStrategy()).isEqualTo(DegradeStrategy.PLANNER_MODEL_UNAVAILABLE);
        assertThat(policy.resolvedModelKey()).isBlank();
        assertThat(policy.modelCapability()).isEqualTo(ModelCapability.UNKNOWN);
        assertThat(policy.blocksPreviewGeneration()).isFalse();
    }

    @Test
    void shouldResolveSimpleComplexityWithHighCapabilityRemoteModel() {
        final ResolvedHeavyModel remoteHighModel = new ResolvedHeavyModel(
                "google:gemini-3-pro",
                "google",
                "gemini",
                CredentialSource.PLATFORM,
                "platform",
                "gemini-3-pro",
                "Gemini 3 Pro",
                false,
                false,
                null,
                null,
                null,
                java.util.List.of("REMOTE"),
                ModelCapability.HIGH);

        final PlanningModelPolicy policy = manager.resolvePolicy(
                PlanningComplexity.SIMPLE,
                remoteHighModel,
                false);

        assertThat(policy.selectedPlanningMode()).isEqualTo(PlanningMode.SINGLE_PASS);
        assertThat(policy.capabilityRisk()).isEqualTo(CapabilityRisk.LOW);
        assertThat(policy.degradeStrategy()).isEqualTo(DegradeStrategy.NONE);
        assertThat(policy.blocksPreviewGeneration()).isFalse();
    }

    @Test
    void shouldResolveModerateComplexityWithLowCapabilityLocalModel() {
        final ResolvedHeavyModel localLowModel = new ResolvedHeavyModel(
                "ollama:mistral",
                "ollama",
                "mistral",
                CredentialSource.PLATFORM,
                "platform",
                "mistral",
                "Mistral Local",
                false,
                false,
                null,
                null,
                null,
                java.util.List.of("LOCAL"),
                ModelCapability.LOW);

        final PlanningModelPolicy policy = manager.resolvePolicy(
                PlanningComplexity.MODERATE,
                localLowModel,
                false);

        assertThat(policy.selectedPlanningMode()).isEqualTo(PlanningMode.SINGLE_PASS);
        assertThat(policy.capabilityRisk()).isEqualTo(CapabilityRisk.ELEVATED);
        assertThat(policy.degradeStrategy()).isEqualTo(DegradeStrategy.REDUCED_PREVIEW);
        assertThat(policy.blocksPreviewGeneration()).isFalse();
    }

    @Test
    void shouldResolveComplexComplexityWithLowCapabilityLocalModel() {
        final ResolvedHeavyModel localLowModel = new ResolvedHeavyModel(
                "ollama:mistral",
                "ollama",
                "mistral",
                CredentialSource.PLATFORM,
                "platform",
                "mistral",
                "Mistral Local",
                false,
                false,
                null,
                null,
                null,
                java.util.List.of("LOCAL"),
                ModelCapability.LOW);

        final PlanningModelPolicy policy = manager.resolvePolicy(
                PlanningComplexity.COMPLEX,
                localLowModel,
                false);

        assertThat(policy.selectedPlanningMode()).isEqualTo(PlanningMode.REDUCED_MULTI_PASS);
        assertThat(policy.capabilityRisk()).isEqualTo(CapabilityRisk.HIGH);
        assertThat(policy.degradeStrategy()).isEqualTo(DegradeStrategy.SKELETON_FIRST_PREVIEW);
        assertThat(policy.blocksPreviewGeneration()).isFalse();
    }

    @Test
    void shouldResolveEnterpriseWarningWithRemoteLowCapabilityModel() {
        final ResolvedHeavyModel remoteLowModel = new ResolvedHeavyModel(
                "groq:mixtral",
                "groq",
                "mixtral",
                CredentialSource.PLATFORM,
                "platform",
                "mixtral",
                "Mixtral Groq",
                false,
                false,
                null,
                null,
                null,
                java.util.List.of("REMOTE"),
                ModelCapability.LOW);

        final PlanningModelPolicy policy = manager.resolvePolicy(
                PlanningComplexity.ENTERPRISE,
                remoteLowModel,
                false);

        assertThat(policy.selectedPlanningMode()).isEqualTo(PlanningMode.REDUCED_MULTI_PASS);
        assertThat(policy.capabilityRisk()).isEqualTo(CapabilityRisk.CRITICAL);
        assertThat(policy.degradeStrategy()).isEqualTo(DegradeStrategy.ENTERPRISE_QUALITY_WARNING);
        assertThat(policy.blocksPreviewGeneration()).isFalse();
    }
}
