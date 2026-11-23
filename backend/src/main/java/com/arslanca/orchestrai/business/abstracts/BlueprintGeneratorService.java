package com.arslanca.orchestrai.business.abstracts;

import com.arslanca.orchestrai.business.dtos.ai.ResolvedHeavyModel;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.planning.blueprint.CanonicalBlueprint;
import com.arslanca.orchestrai.business.runtime.GenerationUsageContext;

/**
 * Stage 2: Generates a plan-only blueprint from structured analysis.
 *
 * <p>
 * Uses the heavy LLM to produce:
 * <ul>
 * <li>Architecture overview</li>
 * <li>Sequential, isolated development phases</li>
 * <li>IDE-native agent profiles</li>
 * <li>IDE rules and starter assembly notes</li>
 * </ul>
 * </p>
 *
 * @throws com.arslanca.orchestrai.core.exception.type.LlmResponseParseException if
 *                                                                               LLM
 *                                                                               response
 *                                                                               cannot
 *                                                                               be
 *                                                                               parsed
 */
public interface BlueprintGeneratorService {

    /**
     * Generates a plan-only blueprint.
     *
     * @param analysis  the structured analysis from Stage 1
     * @param ideTarget target IDE (cursor, antigravity, windsurf, generic)
     * @return the canonical planning model produced by the planner
     */
    CanonicalBlueprint generateBlueprint(
            StructuredAnalysis analysis,
            String ideTarget,
            ResolvedHeavyModel resolvedHeavyModel,
            StarterSelection starterSelection);

    default CanonicalBlueprint generateBlueprint(
            final StructuredAnalysis analysis,
            final String ideTarget,
            final ResolvedHeavyModel resolvedHeavyModel,
            final StarterSelection starterSelection,
            final GenerationUsageContext generationUsageContext) {
        return generateBlueprint(analysis, ideTarget, resolvedHeavyModel, starterSelection);
    }
}
