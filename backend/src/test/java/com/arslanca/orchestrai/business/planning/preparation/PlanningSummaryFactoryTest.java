package com.arslanca.orchestrai.business.planning.preparation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.arslanca.orchestrai.business.concretes.DeterministicPlanningPolicyManager;
import com.arslanca.orchestrai.business.concretes.PlanningModelPolicyManager;
import com.arslanca.orchestrai.business.dtos.ai.ModelCapability;
import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningMode;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningSummary;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.core.ai.provider.CredentialSource;

class PlanningSummaryFactoryTest {

    private final PlanningSummaryFactory factory = new PlanningSummaryFactory(
            null,
            new PlanningModelPolicyManager(),
            new DeterministicPlanningPolicyManager());

    @Test
    void shouldExposeEffectiveComplexityAndExpectedShapeInSummary() {
        final PlanningSummary summary = factory.buildPlanningSummary(
                enterpriseAnalysis(),
                starterSelection(),
                new ResolvedHeavyModel(
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
                        List.of("REMOTE"),
                        ModelCapability.HIGH));

        assertThat(summary.complexity()).isEqualTo(PlanningComplexity.COMPLEX);
        assertThat(summary.effectiveComplexity()).isEqualTo(PlanningComplexity.ENTERPRISE);
        assertThat(summary.selectedPlanningMode()).isEqualTo(PlanningMode.MULTI_PASS_EXPANSION);
        assertThat(summary.expectedShape().outputBand()).isNotBlank();
        assertThat(summary.hardSignals()).contains(PlanningHardSignal.MULTI_TENANCY);
    }

    private StructuredAnalysis enterpriseAnalysis() {
        return new StructuredAnalysis(
                "User Behavior Analytics Platform",
                "saas analytics platform",
                List.of("spring boot", "postgres", "redis", "mongodb"),
                List.of("dashboard", "api", "analytics", "aggregation", "crew provisioning"),
                "complex",
                false,
                "",
                "Multi-tenant self-hosted analytics platform with dashboard and API surfaces.",
                List.of("admin", "crew admin", "analyst"),
                List.of("serve multiple organizations"),
                List.of("ingest events", "review analytics"),
                List.of("web", "api"),
                true,
                true,
                true,
                "self-hosted docker",
                "multi-tenant",
                List.of("postgres", "mongodb", "redis"),
                List.of("sso", "billing"),
                List.of("rbac", "tenant isolation"),
                List.of(),
                "high growth",
                "low latency",
                List.of("local credential management", "customer-managed deployment"),
                List.of(),
                List.of("queue topology"),
                List.of(),
                List.of(),
                List.of(),
                List.of("multi-tenant", "event-driven", "analytics pipeline"),
                StructuredAnalysis.CURRENT_CONTRACT_VERSION);
    }

    private StarterSelection starterSelection() {
        return new StarterSelection(
                java.util.UUID.randomUUID(),
                "User Behavior Analytics Platform",
                "cursor",
                new StarterCandidate(
                        "spring-boot-postgres-api",
                        "Spring Boot Postgres API",
                        "backend",
                        StarterSourceKind.OFFICIAL_ADAPTER,
                        "spring-initializr",
                        List.of("spring-boot", "postgres"),
                        "Best fit for the current backend-heavy slice."),
                List.of(),
                false,
                "");
    }
}
