package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record StructuredAnalysis(
        String projectName,
        String projectType,
        List<String> techStack,
        List<String> features,
        String complexity,
        boolean isMalicious,
        String maliciousReason,
        String summary,

        // New rich planning phase 1 fields
        List<String> actors,
        List<String> businessGoals,
        List<String> userJourneys,
        List<String> deliveryChannels,
        Boolean frontendNeeded,
        Boolean backendNeeded,
        Boolean workerNeeded,
        String deploymentModel,
        String tenancyModel,
        List<String> dataSources,
        List<String> integrationPoints,
        List<String> securityRequirements,
        List<String> complianceRequirements,
        String scaleExpectations,
        String latencyExpectations,
        List<String> operationalConstraints,
        List<String> assumptions,
        List<String> unknowns,
        List<String> openQuestions,
        List<String> unresolvedConstraints,
        List<String> nonGoals,
        List<String> complexityMarkers,
        String contractVersion
) {
    public static final String CURRENT_CONTRACT_VERSION = "2.0";

    public StructuredAnalysis(
            String projectName,
            String projectType,
            List<String> techStack,
            List<String> features,
            String complexity,
            boolean isMalicious,
            String maliciousReason,
            String summary) {
        this(projectName, projectType, techStack, features, complexity, isMalicious, maliciousReason, summary,
                List.of(), List.of(), List.of(), List.of(), null, null, null, "", "",
                List.of(), List.of(), List.of(), List.of(), "", "", List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), CURRENT_CONTRACT_VERSION);
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public StructuredAnalysis {
        projectName = normalizeNullableText(projectName);
        projectType = normalizeNullableText(projectType);
        complexity = normalizeNullableText(complexity);
        maliciousReason = normalizeNullableText(maliciousReason);
        summary = normalizeNullableText(summary);

        techStack = techStack == null ? List.of() : List.copyOf(techStack);
        features = features == null ? List.of() : List.copyOf(features);
        actors = actors == null ? List.of() : List.copyOf(actors);
        businessGoals = businessGoals == null ? List.of() : List.copyOf(businessGoals);
        userJourneys = userJourneys == null ? List.of() : List.copyOf(userJourneys);
        deliveryChannels = deliveryChannels == null ? List.of() : List.copyOf(deliveryChannels);
        deploymentModel = normalizeNullableText(deploymentModel);
        tenancyModel = normalizeNullableText(tenancyModel);
        dataSources = dataSources == null ? List.of() : List.copyOf(dataSources);
        integrationPoints = integrationPoints == null ? List.of() : List.copyOf(integrationPoints);
        securityRequirements = securityRequirements == null ? List.of() : List.copyOf(securityRequirements);
        complianceRequirements = complianceRequirements == null ? List.of() : List.copyOf(complianceRequirements);
        scaleExpectations = normalizeNullableText(scaleExpectations);
        latencyExpectations = normalizeNullableText(latencyExpectations);
        operationalConstraints = operationalConstraints == null ? List.of() : List.copyOf(operationalConstraints);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
        openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
        unresolvedConstraints = unresolvedConstraints == null ? List.of() : List.copyOf(unresolvedConstraints);
        nonGoals = nonGoals == null ? List.of() : List.copyOf(nonGoals);
        complexityMarkers = complexityMarkers == null ? List.of() : List.copyOf(complexityMarkers);
        contractVersion = contractVersion == null || contractVersion.isBlank() ? CURRENT_CONTRACT_VERSION : contractVersion.trim();
    }

    @JsonIgnore
    public boolean isSafe() {
        return !isMalicious;
    }

    @JsonIgnore
    public PlanningComplexity planningComplexity() {
        return PlanningComplexity.fromValue(complexity);
    }

    @JsonIgnore
    public boolean frontendNeededResolved() {
        if (frontendNeeded != null) {
            return frontendNeeded;
        }
        return containsUiSignals(projectType, techStack, deliveryChannels);
    }

    @JsonIgnore
    public boolean backendNeededResolved() {
        if (backendNeeded != null) {
            return backendNeeded;
        }
        if (!dataSources.isEmpty() || !integrationPoints.isEmpty() || !securityRequirements.isEmpty()) {
            return true;
        }
        final String normalizedProjectType = projectType == null ? "" : projectType.toLowerCase();
        if (normalizedProjectType.contains("saas")
                || containsToken(features, "auth")
                || containsToken(features, "billing")
                || containsToken(features, "payment")
                || containsToken(features, "admin")) {
            return true;
        }
        return !containsOnlyUiSignals(normalizedProjectType, techStack, deliveryChannels);
    }

    @JsonIgnore
    public boolean workerNeededResolved() {
        if (workerNeeded != null) {
            return workerNeeded;
        }
        return containsToken(complexityMarkers, "worker")
                || containsToken(complexityMarkers, "async")
                || containsToken(complexityMarkers, "event")
                || containsToken(features, "queue")
                || containsToken(features, "background")
                || containsToken(features, "processing");
    }

    private static boolean containsUiSignals(
            final String projectType,
            final List<String> techStack,
            final List<String> deliveryChannels) {
        final String normalizedProjectType = projectType == null ? "" : projectType.toLowerCase();
        return normalizedProjectType.contains("web")
                || normalizedProjectType.contains("dashboard")
                || normalizedProjectType.contains("frontend")
                || normalizedProjectType.contains("mobile")
                || normalizedProjectType.contains("saas")
                || containsToken(techStack, "next")
                || containsToken(techStack, "react")
                || containsToken(techStack, "vue")
                || containsToken(techStack, "svelte")
                || containsToken(techStack, "angular")
                || containsToken(techStack, "flutter")
                || containsToken(techStack, "expo")
                || containsToken(deliveryChannels, "web")
                || containsToken(deliveryChannels, "mobile");
    }

    private static boolean containsOnlyUiSignals(
            final String projectType,
            final List<String> techStack,
            final List<String> deliveryChannels) {
        return containsUiSignals(projectType, techStack, deliveryChannels)
                && !projectType.contains("api")
                && !projectType.contains("backend")
                && !projectType.contains("service");
    }

    private static boolean containsToken(final List<String> values, final String token) {
        if (values == null || values.isEmpty()) {
            return false;
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toLowerCase())
                .anyMatch(value -> value.contains(token));
    }

    private static String normalizeNullableText(final String value) {
        if (value == null) {
            return null;
        }
        final String normalizedValue = value.trim();
        return normalizedValue.isEmpty() ? null : normalizedValue;
    }
}
