package com.arslanca.orchestrai.business.planning.governance;

import org.springframework.stereotype.Component;

@Component
public class NoOpPlanningTelemetryService implements PlanningTelemetryService {

    @Override
    public void recordSnapshot(final PlanningGovernanceSnapshot snapshot) {
    }
}
