package com.arslanca.orchestrai.business.dtos.orchestration;

import java.util.List;

import com.arslanca.orchestrai.business.enums.ChatPlanningHandoffState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatPlanningSnapshot(
        String projectName,
        String projectType,
        String scopeSummary,
        String stackSummary,
        String targetUserSummary,
        List<String> confirmedScopeDecisions,
        List<String> confirmedStackDecisions,
        List<String> openQuestions,
        List<String> unresolvedConstraints,
        List<String> inferredComplexityMarkers,
        ChatPlanningHandoffState handoffState,
        String handoffSummary,
        String contractVersion) {

    public static final String CURRENT_CONTRACT_VERSION = "3.0";

    public ChatPlanningSnapshot {
        projectName = normalizeText(projectName);
        projectType = normalizeText(projectType);
        scopeSummary = normalizeText(scopeSummary);
        stackSummary = normalizeText(stackSummary);
        targetUserSummary = normalizeText(targetUserSummary);
        confirmedScopeDecisions = confirmedScopeDecisions == null ? List.of() : List.copyOf(confirmedScopeDecisions);
        confirmedStackDecisions = confirmedStackDecisions == null ? List.of() : List.copyOf(confirmedStackDecisions);
        openQuestions = openQuestions == null ? List.of() : List.copyOf(openQuestions);
        unresolvedConstraints = unresolvedConstraints == null ? List.of() : List.copyOf(unresolvedConstraints);
        inferredComplexityMarkers = inferredComplexityMarkers == null ? List.of() : List.copyOf(inferredComplexityMarkers);
        handoffState = handoffState == null ? ChatPlanningHandoffState.CONTINUE_DISCOVERY : handoffState;
        handoffSummary = normalizeText(handoffSummary);
        contractVersion = contractVersion == null || contractVersion.isBlank() ? CURRENT_CONTRACT_VERSION : contractVersion.trim();
    }

    private static String normalizeText(final String value) {
        return value == null ? "" : value.trim();
    }
}
