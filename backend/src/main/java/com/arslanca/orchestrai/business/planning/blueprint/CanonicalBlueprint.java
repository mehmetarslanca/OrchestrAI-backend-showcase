package com.arslanca.orchestrai.business.planning.blueprint;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record CanonicalBlueprint(
    String projectName,
    String executiveSummary,
    String architectureOverview,
    String systemContext,
    List<String> majorArchitectureDecisions,
    List<CanonicalWorkstream> workstreams,
    List<String> nonFunctionalRequirements,
    List<String> crossCuttingConcerns,
    List<String> glossary,
    List<String> assumptions,
    List<String> unknowns,
    List<CanonicalPhase> phases,
    List<CanonicalAgent> agents,
    String ideRules,
    String starterNotes,
    String contractVersion) {
  public static final String CURRENT_CONTRACT_VERSION = "1.0";

  public CanonicalBlueprint {
    projectName = normalizeText(projectName);
    executiveSummary = normalizeText(executiveSummary);
    architectureOverview = normalizeText(architectureOverview);
    systemContext = normalizeText(systemContext);
    majorArchitectureDecisions = majorArchitectureDecisions == null ? List.of()
        : List.copyOf(majorArchitectureDecisions);
    workstreams = workstreams == null ? List.of() : List.copyOf(workstreams);
    nonFunctionalRequirements = nonFunctionalRequirements == null ? List.of() : List.copyOf(nonFunctionalRequirements);
    crossCuttingConcerns = crossCuttingConcerns == null ? List.of() : List.copyOf(crossCuttingConcerns);
    glossary = glossary == null ? List.of() : List.copyOf(glossary);
    assumptions = assumptions == null ? List.of() : List.copyOf(assumptions);
    unknowns = unknowns == null ? List.of() : List.copyOf(unknowns);

    phases = phases == null || phases.isEmpty() ? flattenWorkstreamPhases(workstreams) : List.copyOf(phases);
    agents = agents == null ? List.of() : List.copyOf(agents);
    ideRules = normalizeText(ideRules);
    starterNotes = normalizeText(starterNotes);
    contractVersion = normalizeContractVersion(contractVersion);
  }

  private static List<CanonicalPhase> flattenWorkstreamPhases(final List<CanonicalWorkstream> workstreams) {
    if (workstreams == null || workstreams.isEmpty()) {
      return List.of();
    }

    final List<CanonicalPhase> flattenedPhases = new ArrayList<>();
    for (final CanonicalWorkstream workstream : workstreams) {
      if (workstream == null || workstream.phases() == null) {
        continue;
      }
      flattenedPhases.addAll(workstream.phases());
    }
    return List.copyOf(flattenedPhases);
  }

  private static String normalizeText(final String value) {
    if (value == null) {
      return "";
    }
    return value.trim();
  }

  private static String normalizeContractVersion(final String value) {
    if (value == null || value.isBlank()) {
      return CURRENT_CONTRACT_VERSION;
    }
    return value.trim();
  }
}
