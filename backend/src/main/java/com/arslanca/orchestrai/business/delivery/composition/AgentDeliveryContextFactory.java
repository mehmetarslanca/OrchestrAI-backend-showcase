package com.arslanca.orchestrai.business.delivery.composition;

import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class AgentDeliveryContextFactory {

    private AgentDeliveryContextFactory() {
    }

    public static AgentDeliveryContext resolve(
            final BlueprintPlan plan,
            final BlueprintPlan.AgentDefinition agent) {
        final List<BlueprintPlan.WorkstreamDefinition> matchedWorkstreams = resolveWorkstreams(plan, agent);
        final List<BlueprintPlan.PhaseDefinition> matchedPhases = matchedWorkstreams.stream()
                .flatMap(workstream -> workstream.phases().stream())
                .toList();

        final String role = agent == null ? "" : agent.role();
        final List<String> responsibilities = agent == null ? List.of() : agent.responsibilities();

        return new AgentDeliveryContext(
                buildMission(role, responsibilities),
                buildWhenToUse(role, responsibilities, matchedWorkstreams),
                preferredOrFallback(
                        distinctStrings(matchedWorkstreams.stream()
                                .flatMap(workstream -> workstream.includedScope().stream())
                                .toList(), responsibilities),
                        "Assigned " + role + " responsibilities"),
                preferredOrFallback(
                        distinctStrings(
                                matchedWorkstreams.stream().flatMap(workstream -> workstream.excludedScope().stream()).toList(),
                                matchedPhases.stream().flatMap(phase -> phase.scopeOut().stream()).toList()),
                        "Unapproved work outside the assigned blueprint scope"),
                preferredOrFallback(
                        distinctStrings(
                                matchedWorkstreams.stream().flatMap(workstream -> workstream.dependencies().stream()).toList(),
                                matchedPhases.stream().flatMap(phase -> phase.prerequisites().stream()).toList(),
                                matchedPhases.stream().flatMap(phase -> phase.contracts().stream()).toList()),
                        "Accepted blueprint documents and current implementation context"),
                preferredOrFallback(
                        distinctStrings(
                                matchedPhases.stream().flatMap(phase -> phase.deliverables().stream()).toList(),
                                responsibilities),
                        "Completed implementation handoff for the assigned work"),
                preferredOrFallback(
                        buildQualityChecklist(matchedPhases),
                        "Preserve declared contracts and document unresolved risks before handoff"),
                preferredOrFallback(
                        distinctStrings(matchedPhases.stream().map(BlueprintPlan.PhaseDefinition::handoffInstructions).toList()),
                        "Document completed work, unresolved risks, and next required inputs before handoff"),
                distinctStrings(matchedWorkstreams.stream().map(BlueprintPlan.WorkstreamDefinition::title).toList()),
                distinctStrings(matchedPhases.stream()
                        .map(phase -> DeliveryDocumentIdFactory.phaseId(phase.order(), phase.title()))
                        .toList()));
    }

    private static List<BlueprintPlan.WorkstreamDefinition> resolveWorkstreams(
            final BlueprintPlan plan,
            final BlueprintPlan.AgentDefinition agent) {
        if (plan == null || agent == null || plan.workstreams() == null) {
            return List.of();
        }

        final String normalizedRole = normalizeRole(agent.role());
        return plan.workstreams().stream()
                .filter(workstream -> workstream != null && workstream.suggestedAgents().stream()
                        .map(AgentDeliveryContextFactory::normalizeRole)
                        .anyMatch(normalizedRole::equals))
                .toList();
    }

    private static List<String> buildQualityChecklist(final List<BlueprintPlan.PhaseDefinition> phases) {
        final List<String> qualityChecklist = new ArrayList<>();
        for (final BlueprintPlan.PhaseDefinition phase : phases) {
            qualityChecklist.addAll(phase.acceptanceCriteria());
            phase.securityConsiderations().stream()
                    .map(value -> "Security: " + value)
                    .forEach(qualityChecklist::add);
            if (!phase.testingStrategy().isBlank()) {
                qualityChecklist.add("Testing: " + phase.testingStrategy());
            }
            if (!phase.observabilityPlan().isBlank()) {
                qualityChecklist.add("Observability: " + phase.observabilityPlan());
            }
        }
        return distinctStrings(qualityChecklist);
    }

    private static String buildMission(final String role, final List<String> responsibilities) {
        if (responsibilities != null && !responsibilities.isEmpty()) {
            return "%s delivery scope: %s.".formatted(role, String.join(", ", distinctStrings(responsibilities)));
        }
        return "Own the %s delivery scope for this blueprint.".formatted(role);
    }

    private static String buildWhenToUse(
            final String role,
            final List<String> responsibilities,
            final List<BlueprintPlan.WorkstreamDefinition> matchedWorkstreams) {
        if (!matchedWorkstreams.isEmpty()) {
            return "Use this agent when executing %s.".formatted(String.join(", ", matchedWorkstreams.stream()
                    .map(BlueprintPlan.WorkstreamDefinition::title)
                    .toList()));
        }
        if (responsibilities != null && !responsibilities.isEmpty()) {
            return "Use this agent when the blueprint needs %s.".formatted(responsibilities.getFirst());
        }
        return "Use this agent when the blueprint requires %s support.".formatted(role);
    }

    private static List<String> preferredOrFallback(final List<String> values, final String fallback) {
        if (values != null && !values.isEmpty()) {
            return values;
        }
        return fallback == null || fallback.isBlank() ? List.of() : List.of(fallback);
    }

    @SafeVarargs
    private static List<String> distinctStrings(final List<String>... lists) {
        final Set<String> values = new LinkedHashSet<>();
        if (lists == null) {
            return List.of();
        }
        for (final List<String> list : lists) {
            if (list == null) {
                continue;
            }
            for (final String value : list) {
                if (value != null && !value.isBlank()) {
                    values.add(value.trim());
                }
            }
        }
        return List.copyOf(values);
    }

    private static String normalizeRole(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+|-+$)", "");
    }
}
