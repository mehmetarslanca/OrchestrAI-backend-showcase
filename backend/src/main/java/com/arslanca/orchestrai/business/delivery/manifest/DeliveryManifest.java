package com.arslanca.orchestrai.business.delivery.manifest;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record DeliveryManifest(
        String schemaVersion,
        String projectName,
        String ideTarget,
        String executiveSummary,
        String architectureOverview,
        String systemContext,
        List<String> architectureDecisionSummaries,
        List<ManifestWorkstreamEntry> workstreams,
        List<ManifestPhaseEntry> phases,
        List<ManifestAgentEntry> agents,
        List<String> assumptions,
        List<String> unknowns,
        List<String> globalConstraints,
        String starterSummary,
        List<String> deliveryWarnings,
        int totalDocuments,
        List<ManifestDocumentEntry> inventory) {

    public DeliveryManifest {
        schemaVersion = schemaVersion == null || schemaVersion.isBlank() ? "2.0" : schemaVersion.trim();
        projectName = projectName == null ? "" : projectName.trim();
        ideTarget = ideTarget == null ? "" : ideTarget.trim();
        executiveSummary = executiveSummary == null ? "" : executiveSummary.trim();
        architectureOverview = architectureOverview == null ? "" : architectureOverview.trim();
        systemContext = systemContext == null ? "" : systemContext.trim();
        architectureDecisionSummaries = architectureDecisionSummaries == null ? List.of() : List.copyOf(architectureDecisionSummaries);
        workstreams = workstreams == null ? List.of() : List.copyOf(workstreams);
        phases = phases == null ? List.of() : List.copyOf(phases);
        agents = agents == null ? List.of() : List.copyOf(agents);
        assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
        unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);
        globalConstraints = globalConstraints == null ? List.of() : List.copyOf(globalConstraints);
        starterSummary = starterSummary == null ? "" : starterSummary.trim();
        deliveryWarnings = deliveryWarnings == null ? List.of() : List.copyOf(deliveryWarnings);
        inventory = inventory == null ? List.of() : List.copyOf(inventory);
    }

    public static DeliveryManifest empty() {
        return new DeliveryManifest(
                null,
                "",
                "",
                "",
                "",
                "",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "",
                List.of(),
                0,
                List.of());
    }

    public DeliveryManifest withIdeTargetAndRenderedPaths(
            final String updatedIdeTarget,
            final Map<String, String> renderedDocumentPaths) {
        final Map<String, String> normalizedPaths = renderedDocumentPaths == null
                ? Map.of()
                : Map.copyOf(new LinkedHashMap<>(renderedDocumentPaths));

        final List<ManifestDocumentEntry> renderedInventory = inventory.stream()
                .map(entry -> entry.withRenderedPath(normalizedPaths.getOrDefault(entry.id(), "")))
                .toList();

        return new DeliveryManifest(
                schemaVersion,
                projectName,
                updatedIdeTarget,
                executiveSummary,
                architectureOverview,
                systemContext,
                architectureDecisionSummaries,
                workstreams,
                phases,
                agents,
                assumptions,
                unknowns,
                globalConstraints,
                starterSummary,
                deliveryWarnings,
                renderedInventory.size(),
                renderedInventory);
    }
}
