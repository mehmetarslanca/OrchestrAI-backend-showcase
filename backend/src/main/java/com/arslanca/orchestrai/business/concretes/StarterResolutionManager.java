package com.arslanca.orchestrai.business.concretes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.StarterCatalogService;
import com.arslanca.orchestrai.business.abstracts.StarterResolutionService;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterManifest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.prompt.PromptTemplates;
import com.arslanca.orchestrai.business.prompt.SystemPrompts;

@Service
public class StarterResolutionManager implements StarterResolutionService {

    private static final int MIN_MATCH_SCORE = 20;
    private static final int MAX_CANDIDATES = 3;
    private static final int AI_EXPLANATION_DELTA = 15;
    private static final Logger log = LoggerFactory.getLogger(StarterResolutionManager.class);

    private final StarterCatalogService starterCatalogService;
    private final ChatModel lightChatModel;

    public StarterResolutionManager(
            final StarterCatalogService starterCatalogService,
            final ChatModel lightChatModel) {
        this.starterCatalogService = starterCatalogService;
        this.lightChatModel = lightChatModel;
    }

    @Override
    public StarterSelection resolveStarter(final StructuredAnalysis analysis, final String ideTarget) {
        final AnalysisSignals analysisSignals = AnalysisSignals.from(analysis);
        final String normalizedIdeTarget = normalizePhrase(ideTarget);

        final List<ScoredStarter> rankedCandidates = starterCatalogService.getApprovedStarters().stream()
                .filter(StarterManifest::isSelectable)
                .filter(starter -> starter.supportsIdeTarget(normalizedIdeTarget))
                .map(starter -> scoreStarter(starter, analysisSignals))
                .filter(scoredStarter -> scoredStarter.matchScore() >= MIN_MATCH_SCORE)
                .sorted(Comparator
                        .comparingInt(ScoredStarter::matchScore).reversed()
                        .thenComparing(scoredStarter -> scoredStarter.manifest().priority(), Comparator.reverseOrder())
                        .thenComparing(scoredStarter -> scoredStarter.manifest().key()))
                .limit(MAX_CANDIDATES)
                .toList();

        if (rankedCandidates.isEmpty()) {
            return docsOnlySelection(analysisSignals, normalizedIdeTarget);
        }

        final ScoredStarter recommendedStarter = rankedCandidates.get(0);
        final List<ScoredStarter> alternativeStarters = rankedCandidates.stream().skip(1).toList();

        final StarterCandidate recommendedCandidate = recommendedStarter.manifest()
                .toCandidate(buildRecommendationReason(recommendedStarter, alternativeStarters, analysisSignals));

        final List<StarterCandidate> alternatives = alternativeStarters.stream()
                .map(scoredStarter -> scoredStarter.manifest().toCandidate(buildAlternativeReason(scoredStarter)))
                .toList();

        return new StarterSelection(
                UUID.randomUUID(),
                analysisSignals.projectName(),
                normalizedIdeTarget,
                recommendedCandidate,
                alternatives,
                false,
                "");
    }

    private StarterSelection docsOnlySelection(final AnalysisSignals analysisSignals, final String ideTarget) {
        return new StarterSelection(
                UUID.randomUUID(),
                analysisSignals.projectName(),
                ideTarget,
                null,
                List.of(),
                true,
                "No confident approved starter matched the requested stack for %s. Proceeding with architecture-only blueprint generation."
                        .formatted(ideTarget));
    }

    private ScoredStarter scoreStarter(final StarterManifest manifest, final AnalysisSignals signals) {
        int score = 0;
        final LinkedHashSet<String> matchedSignals = new LinkedHashSet<>();

        for (final String manifestProjectType : manifest.projectTypes()) {
            if (matchesPhrase(manifestProjectType, List.of(signals.projectType()))) {
                score += 40;
                matchedSignals.add(manifestProjectType);
                break;
            }
        }

        if (matchesPhrase(manifest.family(), signals.primarySignals())) {
            score += 25;
            matchedSignals.add(manifest.family());
        }

        for (final String stackTag : manifest.stackTags()) {
            if (matchesPhrase(stackTag, signals.techStack())) {
                score += 20;
                matchedSignals.add(stackTag);
            } else if (matchesPhrase(stackTag, signals.secondarySignals())) {
                score += 5;
                matchedSignals.add(stackTag);
            }
        }

        return new ScoredStarter(manifest, score, List.copyOf(matchedSignals));
    }

    private String buildRecommendationReason(
            final ScoredStarter recommendedStarter,
            final List<ScoredStarter> alternatives,
            final AnalysisSignals signals) {
        if (shouldAskForAiExplanation(recommendedStarter, alternatives)) {
            final String aiReason = tryGenerateAiExplanation(recommendedStarter, alternatives, signals);
            if (!aiReason.isBlank()) {
                return aiReason;
            }
        }
        return deterministicReason(recommendedStarter);
    }

    private boolean shouldAskForAiExplanation(final ScoredStarter recommendedStarter, final List<ScoredStarter> alternatives) {
        if (alternatives.isEmpty()) {
            return false;
        }
        return recommendedStarter.matchScore() - alternatives.get(0).matchScore() <= AI_EXPLANATION_DELTA;
    }

    private String tryGenerateAiExplanation(
            final ScoredStarter recommendedStarter,
            final List<ScoredStarter> alternatives,
            final AnalysisSignals signals) {
        try {
            final Prompt prompt = new Prompt(List.of(
                    new SystemMessage(SystemPrompts.STARTER_RECOMMENDATION_EXPLAINER),
                    new UserMessage(PromptTemplates.starterRecommendationExplanationPrompt(
                            signals.projectName(),
                            signals.projectType(),
                            signals.techStack(),
                            signals.features(),
                            recommendedStarter.manifest(),
                            alternatives.stream().map(ScoredStarter::manifest).toList(),
                            recommendedStarter.matchedSignals()))));
            final String rawReason = lightChatModel.call(prompt).getResult().getOutput().getText();
            return sanitizeReason(rawReason);
        } catch (final Exception exception) {
            log.warn("Starter explanation AI assist failed, falling back to deterministic reason: {}",
                    exception.getMessage());
            return "";
        }
    }

    private String buildAlternativeReason(final ScoredStarter alternativeStarter) {
        final String matchedSignals = String.join(", ", alternativeStarter.matchedSignals());
        if (matchedSignals.isBlank()) {
            return "Alternative approved starter for the detected project profile.";
        }
        return "Alternative approved starter matching %s.".formatted(matchedSignals);
    }

    private String deterministicReason(final ScoredStarter recommendedStarter) {
        final String matchedSignals = String.join(", ", recommendedStarter.matchedSignals());
        if (matchedSignals.isBlank()) {
            return "Highest-priority approved starter for the detected project profile.";
        }
        return "Matches %s for the detected project requirements.".formatted(matchedSignals);
    }

    private String sanitizeReason(final String rawReason) {
        if (rawReason == null || rawReason.isBlank()) {
            return "";
        }
        final String sanitizedReason = rawReason
                .replace("`", "")
                .replace("<", "")
                .replace(">", "")
                .replaceAll("[\\r\\n]+", " ")
                .replaceAll("\\s{2,}", " ")
                .trim();
        if (sanitizedReason.length() > 240) {
            return sanitizedReason.substring(0, 240).trim();
        }
        return sanitizedReason;
    }

    private boolean matchesPhrase(final String expectedPhrase, final Collection<String> values) {
        final String normalizedExpected = normalizePhrase(expectedPhrase);
        if (normalizedExpected.isBlank()) {
            return false;
        }
        for (final String value : values) {
            final String normalizedValue = normalizePhrase(value);
            if (normalizedValue.isBlank()) {
                continue;
            }
            if (normalizedExpected.equals(normalizedValue)) {
                return true;
            }
            if (normalizedExpected.length() >= 4 && normalizedValue.contains(normalizedExpected)) {
                return true;
            }
            if (normalizedValue.length() >= 4 && normalizedExpected.contains(normalizedValue)) {
                return true;
            }
        }
        return false;
    }

    private static String normalizePhrase(final String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private record ScoredStarter(StarterManifest manifest, int matchScore, List<String> matchedSignals) {
    }

    private record AnalysisSignals(
            String projectName,
            String projectType,
            List<String> techStack,
            List<String> features,
            List<String> primarySignals,
            List<String> secondarySignals) {

        static AnalysisSignals from(final StructuredAnalysis analysis) {
            final List<String> normalizedTechStack = normalizeValues(analysis == null ? List.of() : analysis.techStack());
            final List<String> normalizedFeatures = normalizeValues(analysis == null ? List.of() : analysis.features());
            final String normalizedProjectType = normalizePhrase(analysis == null ? null : analysis.projectType());
            final String normalizedSummary = normalizePhrase(analysis == null ? null : analysis.summary());

            final List<String> primarySignals = new ArrayList<>(normalizedTechStack);
            if (!normalizedProjectType.isBlank()) {
                primarySignals.add(normalizedProjectType);
            }

            final List<String> secondarySignals = new ArrayList<>(normalizedFeatures);
            if (!normalizedSummary.isBlank()) {
                secondarySignals.add(normalizedSummary);
            }

            return new AnalysisSignals(
                    normalizeProjectName(analysis == null ? null : analysis.projectName()),
                    normalizedProjectType,
                    List.copyOf(new LinkedHashSet<>(normalizedTechStack)),
                    List.copyOf(new LinkedHashSet<>(normalizedFeatures)),
                    List.copyOf(new LinkedHashSet<>(primarySignals)),
                    List.copyOf(new LinkedHashSet<>(secondarySignals)));
        }

        private static List<String> normalizeValues(final List<String> values) {
            if (values == null || values.isEmpty()) {
                return List.of();
            }
            return values.stream()
                    .filter(Objects::nonNull)
                    .map(StarterResolutionManager::normalizePhrase)
                    .filter(value -> !value.isBlank())
                    .distinct()
                    .collect(Collectors.toList());
        }

        private static String normalizeProjectName(final String projectName) {
            if (projectName == null || projectName.isBlank()) {
                return "unnamed-project";
            }
            return projectName.trim();
        }
    }
}
