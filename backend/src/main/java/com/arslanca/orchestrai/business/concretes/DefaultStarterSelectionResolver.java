package com.arslanca.orchestrai.business.concretes;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.StarterSelectionResolver;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCandidate;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;

@Service
public class DefaultStarterSelectionResolver implements StarterSelectionResolver {

    @Override
    public StarterSelection resolveSelectedStarter(final StarterSelection originalSelection, final String requestedStarterKey) {
        if (requestedStarterKey == null || requestedStarterKey.isBlank()) {
            return originalSelection;
        }

        final String normalizedStarterKey = requestedStarterKey.trim().toLowerCase();
        if (originalSelection.docsOnlyFallback()) {
            throw new IllegalArgumentException("starterKey cannot be used when docs-only fallback mode is active");
        }

        if (originalSelection.recommendedStarter() != null
                && normalizedStarterKey.equals(originalSelection.recommendedStarter().key())) {
            return originalSelection;
        }

        final Optional<StarterCandidate> overriddenStarter = originalSelection.alternatives().stream()
                .filter(candidate -> normalizedStarterKey.equals(candidate.key()))
                .findFirst();
        if (overriddenStarter.isEmpty()) {
            throw new IllegalArgumentException("starterKey is not a valid option for this preparation");
        }

        final List<StarterCandidate> alternatives = new ArrayList<>();
        if (originalSelection.recommendedStarter() != null) {
            alternatives.add(originalSelection.recommendedStarter());
        }
        originalSelection.alternatives().stream()
                .filter(candidate -> !normalizedStarterKey.equals(candidate.key()))
                .forEach(alternatives::add);

        return new StarterSelection(
                originalSelection.preparationId(),
                originalSelection.projectName(),
                originalSelection.ideTarget(),
                overriddenStarter.get(),
                List.copyOf(alternatives),
                false,
                "");
    }
}
