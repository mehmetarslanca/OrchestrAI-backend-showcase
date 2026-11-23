package com.arslanca.orchestrai.business.planning.preparation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import org.springframework.stereotype.Component;

import com.arslanca.orchestrai.business.abstracts.DeterministicPlanningPolicyService;
import com.arslanca.orchestrai.business.dtos.orchestration.DeterministicPlanningProfile;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningComplexity;
import com.arslanca.orchestrai.business.dtos.orchestration.PlanningHardSignal;
import com.arslanca.orchestrai.business.dtos.orchestration.RequirementCompletenessReport;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;

@Component
public class RequirementCompletenessPolicy {

    private final DeterministicPlanningPolicyService deterministicPlanningPolicyService;

    public RequirementCompletenessPolicy(final DeterministicPlanningPolicyService deterministicPlanningPolicyService) {
        this.deterministicPlanningPolicyService = deterministicPlanningPolicyService;
    }

    public RequirementCompletenessReport evaluateRequirementCompleteness(final StructuredAnalysis analysis) {
        if (analysis == null) {
            return new RequirementCompletenessReport(
                    false,
                    List.of("structured analysis"),
                    List.of(),
                    List.of(),
                    RequirementCompletenessReport.CURRENT_CONTRACT_VERSION);
        }

        final LinkedHashSet<String> missingRequiredSignals = new LinkedHashSet<>();
        final LinkedHashSet<String> unresolvedRequiredSignals = new LinkedHashSet<>();
        final List<String> explicitUnknownSignals = collectExplicitUnknownSignals(analysis);

        final DeterministicPlanningProfile profile = deterministicPlanningPolicyService.deriveProfile(analysis);
        final PlanningComplexity effectiveComplexity = profile.effectiveComplexity();

        if (analysis.projectName() == null || analysis.projectName().isBlank()) {
            missingRequiredSignals.add("project scope / name");
        }

        if (analysis.projectType() == null || analysis.projectType().isBlank()) {
            missingRequiredSignals.add("project type");
        }

        if (analysis.summary() == null || analysis.summary().isBlank()) {
            missingRequiredSignals.add("implementation summary");
        }

        if (analysis.techStack() == null || analysis.techStack().isEmpty()) {
            missingRequiredSignals.add("tech stack direction");
        }
        if (analysis.features() == null || analysis.features().isEmpty()) {
            missingRequiredSignals.add("core features");
        }
        if (analysis.deliveryChannels().isEmpty() && !analysis.frontendNeededResolved() && !analysis.backendNeededResolved()
                && !analysis.workerNeededResolved()) {
            missingRequiredSignals.add("delivery channels or runtime shape");
        }

        if (requiresDeploymentClarification(analysis, profile)
                && (analysis.deploymentModel() == null || analysis.deploymentModel().isBlank())
                && !containsAnyToken(explicitUnknownSignals, "deploy", "host", "hosting", "self-host", "infrastructure", "on-prem")) {
            unresolvedRequiredSignals.add("deployment / hosting model");
        }

        if (requiresTenancyClarification(analysis, profile)
                && (analysis.tenancyModel() == null || analysis.tenancyModel().isBlank())
                && !containsAnyToken(explicitUnknownSignals, "tenant", "workspace", "organization", "org")) {
            unresolvedRequiredSignals.add("tenancy model");
        }

        if (requiresAuthClarification(analysis, profile)
                && analysis.securityRequirements().isEmpty()
                && !containsAnyToken(explicitUnknownSignals, "auth", "authorization", "permission", "role", "access")) {
            unresolvedRequiredSignals.add("authentication / authorization model");
        }

        if (requiresDataClarification(analysis, profile)
                && analysis.dataSources().isEmpty()
                && !containsAnyToken(explicitUnknownSignals, "data", "database", "storage", "persistence")) {
            unresolvedRequiredSignals.add("data sources / persistence strategy");
        }

        if (effectiveComplexity.isAtLeast(PlanningComplexity.COMPLEX)
                && requiresOperationalClarification(analysis, profile)
                && !containsAnyToken(explicitUnknownSignals, "ops", "operation", "runbook", "observability", "deployment", "infrastructure")) {
            if (analysis.operationalConstraints().isEmpty()) {
                unresolvedRequiredSignals.add("operational / infrastructure constraints");
            }
        }

        return new RequirementCompletenessReport(
                missingRequiredSignals.isEmpty() && unresolvedRequiredSignals.isEmpty(),
                List.copyOf(missingRequiredSignals),
                List.copyOf(unresolvedRequiredSignals),
                explicitUnknownSignals,
                RequirementCompletenessReport.CURRENT_CONTRACT_VERSION);
    }

    public void assertPreparationReady(final RequirementCompletenessReport completenessReport) {
        if (completenessReport.ready()) {
            return;
        }
        if (!completenessReport.missingRequiredSignals().isEmpty()) {
            throw new IllegalArgumentException(
                    "Project requirements are not ready for generation review yet. Missing: "
                            + String.join(", ", completenessReport.missingRequiredSignals()));
        }
        throw new IllegalArgumentException(
                "Project requirements are not ready for generation review yet. Unresolved: "
                        + String.join(", ", completenessReport.unresolvedRequiredSignals()));
    }

    private List<String> collectExplicitUnknownSignals(final StructuredAnalysis analysis) {
        final LinkedHashSet<String> values = new LinkedHashSet<>();
        values.addAll(safeValues(analysis.assumptions()));
        values.addAll(safeValues(analysis.unknowns()));
        values.addAll(safeValues(analysis.openQuestions()));
        values.addAll(safeValues(analysis.unresolvedConstraints()));
        return List.copyOf(values);
    }

    private List<String> safeValues(final List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .toList();
    }

    private boolean containsAnyToken(final List<String> values, final String... tokens) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        for (final String value : values) {
            final String normalized = value == null ? "" : value.trim().toLowerCase();
            for (final String token : tokens) {
                if (!normalized.isBlank() && normalized.contains(token)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean requiresDeploymentClarification(
            final StructuredAnalysis analysis,
            final DeterministicPlanningProfile profile) {
        return profile.effectiveComplexity().isAtLeast(PlanningComplexity.COMPLEX)
                || profile.hardSignals().contains(PlanningHardSignal.SELF_HOSTED_DEPLOYMENT)
                || !analysis.operationalConstraints().isEmpty();
    }

    private boolean requiresTenancyClarification(
            final StructuredAnalysis analysis,
            final DeterministicPlanningProfile profile) {
        return profile.hardSignals().contains(PlanningHardSignal.MULTI_TENANCY)
                || containsAnyToken(analysis.complexityMarkers(), "multi-tenant")
                || containsAnyToken(analysis.features(), "tenant", "workspace", "organization")
                || containsAnyToken(analysis.businessGoals(), "b2b", "team", "organization");
    }

    private boolean requiresAuthClarification(
            final StructuredAnalysis analysis,
            final DeterministicPlanningProfile profile) {
        return profile.hardSignals().contains(PlanningHardSignal.RBAC_AND_PROVISIONING)
                || containsAnyToken(analysis.features(), "auth", "login", "admin", "billing", "payment")
                || containsAnyToken(analysis.complexityMarkers(), "multi-role auth")
                || containsAnyToken(analysis.securityRequirements(), "auth", "role", "permission", "access");
    }

    private boolean requiresDataClarification(
            final StructuredAnalysis analysis,
            final DeterministicPlanningProfile profile) {
        return profile.hardSignals().contains(PlanningHardSignal.MULTIPLE_DATASTORES_OR_INTEGRATIONS)
                || profile.hardSignals().contains(PlanningHardSignal.ANALYTICS_AND_QUERY_SERVING)
                || analysis.backendNeededResolved()
                || analysis.workerNeededResolved()
                || !analysis.integrationPoints().isEmpty()
                || containsAnyToken(analysis.features(), "project", "chat", "billing", "analytics", "queue");
    }

    private boolean requiresOperationalClarification(
            final StructuredAnalysis analysis,
            final DeterministicPlanningProfile profile) {
        return profile.effectiveComplexity() == PlanningComplexity.ENTERPRISE
                || profile.hardSignals().contains(PlanningHardSignal.SELF_HOSTED_DEPLOYMENT)
                || !analysis.complianceRequirements().isEmpty();
    }
}
