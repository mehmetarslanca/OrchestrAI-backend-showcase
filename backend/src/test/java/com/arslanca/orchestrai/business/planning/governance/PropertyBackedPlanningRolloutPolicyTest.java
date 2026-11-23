package com.arslanca.orchestrai.business.planning.governance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.entity.concretes.ProjectGenerationPreparationEntity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningModelPolicy;
import com.arslanca.orchestrai.core.config.projectgeneration.PlanningGovernanceProperties;

class PropertyBackedPlanningRolloutPolicyTest {

    private PlanningGovernanceProperties properties;
    private PropertyBackedPlanningRolloutPolicy policy;
    private PlanningGovernanceContext context;

    @BeforeEach
    void setUp() {
        properties = new PlanningGovernanceProperties();
        properties.setRolloutMode("off");
        properties.setMinimumComplexity("moderate");
        properties.setRolloutPercentage(100);
        properties.setIncludeLocalModels(true);
        properties.setKillSwitch(false);

        policy = new PropertyBackedPlanningRolloutPolicy(properties);

        context = mock(PlanningGovernanceContext.class);
        var analysis = mock(StructuredAnalysis.class);
        when(analysis.planningComplexity()).thenReturn(PlanningComplexity.COMPLEX);
        when(context.structuredAnalysis()).thenReturn(analysis);
        
        var prep = mock(ProjectGenerationPreparationEntity.class);
        when(prep.getId()).thenReturn(UUID.randomUUID());
        when(context.preparation()).thenReturn(prep);
        
        var modelPolicy = mock(PlanningModelPolicy.class);
        when(modelPolicy.localModel()).thenReturn(false);
        when(context.planningModelPolicy()).thenReturn(modelPolicy);
    }

    @Nested
    class Given_KillSwitchActive {
        @Test
        void shouldRejectImmediately() {
            properties.setKillSwitch(true);
            properties.setRolloutMode("enforced");

            PlanningRolloutDecision decision = policy.resolveDecision(context);

            assertThat(decision.mode()).isEqualTo(PlanningRolloutMode.OFF);
            assertThat(decision.reason()).isEqualTo("kill_switch_active");
            assertThat(decision.killSwitchActive()).isTrue();
        }
    }

    @Nested
    class Given_ComplexityBelowMinimum {
        @Test
        void shouldRejectRollout() {
            properties.setMinimumComplexity("complex");
            
            var analysis = mock(StructuredAnalysis.class);
            when(analysis.planningComplexity()).thenReturn(PlanningComplexity.MODERATE); // below high
            when(context.structuredAnalysis()).thenReturn(analysis);

            PlanningRolloutDecision decision = policy.resolveDecision(context);

            assertThat(decision.mode()).isEqualTo(PlanningRolloutMode.OFF);
            assertThat(decision.reason()).isEqualTo("complexity_below_minimum");
        }
    }

    @Nested
    class Given_LocalModelExcluded {
        @Test
        void shouldRejectLocalModelsIfDisabled() {
            properties.setIncludeLocalModels(false);
            properties.setRolloutMode("enforced");
            
            var modelPolicy = mock(PlanningModelPolicy.class);
            when(modelPolicy.localModel()).thenReturn(true);
            when(context.planningModelPolicy()).thenReturn(modelPolicy);

            PlanningRolloutDecision decision = policy.resolveDecision(context);

            assertThat(decision.mode()).isEqualTo(PlanningRolloutMode.OFF);
            assertThat(decision.reason()).isEqualTo("local_model_excluded");
        }
    }

    @Nested
    class Given_CohortMatching {
        @Test
        void shouldEnforceWhenCohortMatches() {
            properties.setRolloutMode("enforced");
            properties.setRolloutPercentage(100); // guarantees match

            PlanningRolloutDecision decision = policy.resolveDecision(context);

            assertThat(decision.mode()).isEqualTo(PlanningRolloutMode.ENFORCED);
            assertThat(decision.cohortMatched()).isTrue();
            assertThat(decision.enforced()).isTrue();
            assertThat(decision.reason()).isEqualTo("cohort_enforced");
        }

        @Test
        void shouldFallbackToShadowWhenCohortMisses() {
            properties.setRolloutMode("enforced");
            properties.setRolloutPercentage(0); // guarantees miss

            PlanningRolloutDecision decision = policy.resolveDecision(context);

            assertThat(decision.mode()).isEqualTo(PlanningRolloutMode.SHADOW);
            assertThat(decision.cohortMatched()).isFalse();
            assertThat(decision.enforced()).isFalse();
            assertThat(decision.reason()).isEqualTo("cohort_shadow_only");
        }
    }
}
