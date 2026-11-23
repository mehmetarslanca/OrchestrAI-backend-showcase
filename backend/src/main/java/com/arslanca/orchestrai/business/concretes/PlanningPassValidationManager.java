package com.arslanca.orchestrai.business.concretes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.PlanningPassValidationService;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalPhase;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalWorkstream;
import com.arslanca.orchestrai.core.exception.type.LlmResponseValidationException;

@Service
public class PlanningPassValidationManager implements PlanningPassValidationService {

    @Override
    public void validateWorkstreamDecomposition(
            final String passName,
            final List<CanonicalWorkstream> workstreams,
            final int requiredWorkstreamFloor) {
        final WorkstreamValidationSummary summary = summarizeWorkstreams(workstreams);
        if (summary.namedWorkstreamCount() < requiredWorkstreamFloor
                || summary.blankTitleCount() > 0
                || !summary.duplicateTitles().isEmpty()) {
            throw new LlmResponseValidationException(buildWorkstreamFailureMessage(passName, summary, requiredWorkstreamFloor));
        }
    }

    @Override
    public void validatePhaseExpansionViability(
            final String passName,
            final List<CanonicalWorkstream> expandedWorkstreams) {
        final WorkstreamValidationSummary summary = summarizeWorkstreams(expandedWorkstreams);
        if (summary.namedWorkstreamCount() < 1
                || summary.blankTitleCount() > 0
                || !summary.duplicateTitles().isEmpty()
                || summary.totalPhases() < 1) {
            throw new LlmResponseValidationException(buildPhaseExpansionFailureMessage(
                    passName,
                    new PhaseCoverageValidation(List.of(), List.of(), summary.totalPhases()),
                    1,
                    summary));
        }
    }

    @Override
    public void validatePhaseExpansion(
            final String passName,
            final List<CanonicalWorkstream> expectedWorkstreams,
            final List<CanonicalWorkstream> expandedWorkstreams,
            final int requiredPhaseFloor) {
        final WorkstreamValidationSummary summary = summarizeWorkstreams(expandedWorkstreams);
        final PhaseCoverageValidation coverageValidation = inspectPhaseExpansionCoverage(expectedWorkstreams, expandedWorkstreams);
        if (summary.blankTitleCount() > 0
                || !summary.duplicateTitles().isEmpty()
                || coverageValidation.hasWorkstreamCoverageGap()
                || coverageValidation.totalPhases() < requiredPhaseFloor) {
            throw new LlmResponseValidationException(buildPhaseExpansionFailureMessage(
                    passName,
                    coverageValidation,
                    requiredPhaseFloor,
                    summary));
        }
    }

    private PhaseCoverageValidation inspectPhaseExpansionCoverage(
            final List<CanonicalWorkstream> expectedWorkstreams,
            final List<CanonicalWorkstream> expandedWorkstreams) {
        if (expectedWorkstreams == null || expectedWorkstreams.isEmpty()) {
            return new PhaseCoverageValidation(List.of(), List.of(), flattenWorkstreamPhases(expandedWorkstreams).size());
        }

        final Map<String, CanonicalWorkstream> expandedWorkstreamsByTitle = indexWorkstreamsByTitle(expandedWorkstreams);
        final List<String> missingWorkstreams = new ArrayList<>();
        final List<String> workstreamsWithoutPhases = new ArrayList<>();

        for (final CanonicalWorkstream expectedWorkstream : expectedWorkstreams) {
            if (expectedWorkstream == null || expectedWorkstream.title().isBlank()) {
                continue;
            }

            final CanonicalWorkstream expandedWorkstream = expandedWorkstreamsByTitle.get(
                    normalizeWorkstreamKey(expectedWorkstream.title()));
            if (expandedWorkstream == null) {
                missingWorkstreams.add(expectedWorkstream.title());
                continue;
            }
            if (expandedWorkstream.phases().isEmpty()) {
                workstreamsWithoutPhases.add(expectedWorkstream.title());
            }
        }

        return new PhaseCoverageValidation(
                List.copyOf(missingWorkstreams),
                List.copyOf(workstreamsWithoutPhases),
                flattenWorkstreamPhases(expandedWorkstreams).size());
    }

    private WorkstreamValidationSummary summarizeWorkstreams(final List<CanonicalWorkstream> workstreams) {
        if (workstreams == null || workstreams.isEmpty()) {
            return new WorkstreamValidationSummary(0, 0, List.of(), 0);
        }

        int namedWorkstreamCount = 0;
        int blankTitleCount = 0;
        int totalPhases = 0;
        final Map<String, Integer> workstreamTitleCounts = new LinkedHashMap<>();

        for (final CanonicalWorkstream workstream : workstreams) {
            if (workstream == null || workstream.title().isBlank()) {
                blankTitleCount++;
                continue;
            }

            namedWorkstreamCount++;
            totalPhases += workstream.phases().size();
            final String normalizedTitle = normalizeWorkstreamKey(workstream.title());
            workstreamTitleCounts.merge(normalizedTitle, 1, Integer::sum);
        }

        final List<String> duplicateTitles = workstreamTitleCounts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();
        return new WorkstreamValidationSummary(
                namedWorkstreamCount,
                blankTitleCount,
                duplicateTitles,
                totalPhases);
    }

    private Map<String, CanonicalWorkstream> indexWorkstreamsByTitle(final List<CanonicalWorkstream> workstreams) {
        final Map<String, CanonicalWorkstream> indexedWorkstreams = new LinkedHashMap<>();
        if (workstreams == null || workstreams.isEmpty()) {
            return indexedWorkstreams;
        }

        for (final CanonicalWorkstream workstream : workstreams) {
            if (workstream == null || workstream.title().isBlank()) {
                continue;
            }
            indexedWorkstreams.putIfAbsent(normalizeWorkstreamKey(workstream.title()), workstream);
        }
        return indexedWorkstreams;
    }

    private List<CanonicalPhase> flattenWorkstreamPhases(final List<CanonicalWorkstream> workstreams) {
        if (workstreams == null || workstreams.isEmpty()) {
            return List.of();
        }

        return workstreams.stream().flatMap(workstream -> workstream.phases().stream()).toList();
    }

    private String normalizeWorkstreamKey(final String title) {
        if (title == null) {
            return "";
        }
        return title.trim().toLowerCase(Locale.ROOT);
    }

    private String buildWorkstreamFailureMessage(
            final String passName,
            final WorkstreamValidationSummary summary,
            final int requiredWorkstreamFloor) {
        final StringBuilder messageBuilder = new StringBuilder("Workstream decomposition validation failed for ")
                .append(passName)
                .append(": ");
        if (summary.namedWorkstreamCount() < requiredWorkstreamFloor) {
            messageBuilder.append("total workstreams=")
                    .append(summary.namedWorkstreamCount())
                    .append(" below required floor=")
                    .append(requiredWorkstreamFloor)
                    .append("; ");
        }
        appendCommonWorkstreamFailures(messageBuilder, summary);
        return messageBuilder.toString().trim();
    }

    private String buildPhaseExpansionFailureMessage(
            final String passName,
            final PhaseCoverageValidation coverageValidation,
            final int requiredPhaseFloor,
            final WorkstreamValidationSummary summary) {
        final StringBuilder messageBuilder = new StringBuilder("Phase expansion validation failed for ")
                .append(passName)
                .append(": ");
        if (!coverageValidation.missingWorkstreams().isEmpty()) {
            messageBuilder.append("missing workstreams=")
                    .append(String.join(", ", coverageValidation.missingWorkstreams()))
                    .append("; ");
        }
        if (!coverageValidation.workstreamsWithoutPhases().isEmpty()) {
            messageBuilder.append("workstreams without phases=")
                    .append(String.join(", ", coverageValidation.workstreamsWithoutPhases()))
                    .append("; ");
        }
        if (coverageValidation.totalPhases() < requiredPhaseFloor) {
            messageBuilder.append("total phases=")
                    .append(coverageValidation.totalPhases())
                    .append(" below required floor=")
                    .append(requiredPhaseFloor)
                    .append("; ");
        }
        appendCommonWorkstreamFailures(messageBuilder, summary);
        return messageBuilder.toString().trim();
    }

    private void appendCommonWorkstreamFailures(
            final StringBuilder messageBuilder,
            final WorkstreamValidationSummary summary) {
        if (summary.blankTitleCount() > 0) {
            messageBuilder.append("blank workstream titles=")
                    .append(summary.blankTitleCount())
                    .append("; ");
        }
        if (!summary.duplicateTitles().isEmpty()) {
            messageBuilder.append("duplicate workstream titles=")
                    .append(String.join(", ", summary.duplicateTitles()))
                    .append("; ");
        }
    }

    private record WorkstreamValidationSummary(
            int namedWorkstreamCount,
            int blankTitleCount,
            List<String> duplicateTitles,
            int totalPhases) {
    }

    private record PhaseCoverageValidation(
            List<String> missingWorkstreams,
            List<String> workstreamsWithoutPhases,
            int totalPhases) {

        private boolean hasWorkstreamCoverageGap() {
            return !missingWorkstreams.isEmpty() || !workstreamsWithoutPhases.isEmpty();
        }
    }
}
