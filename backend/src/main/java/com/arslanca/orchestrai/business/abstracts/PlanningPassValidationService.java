package com.arslanca.orchestrai.business.abstracts;

import java.util.List;

import com.arslanca.orchestrai.business.planning.blueprint.CanonicalWorkstream;

public interface PlanningPassValidationService {

    void validateWorkstreamDecomposition(
            String passName,
            List<CanonicalWorkstream> workstreams,
            int requiredWorkstreamFloor);

    void validatePhaseExpansionViability(
            String passName,
            List<CanonicalWorkstream> expandedWorkstreams);

    void validatePhaseExpansion(
            String passName,
            List<CanonicalWorkstream> expectedWorkstreams,
            List<CanonicalWorkstream> expandedWorkstreams,
            int requiredPhaseFloor);
}
