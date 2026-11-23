package com.arslanca.orchestrai.business.planning.governance;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class StaticExperimentFlagService implements ExperimentFlagService {

    private final boolean v2Enabled;

    public StaticExperimentFlagService() {
        this(false);
    }

    public StaticExperimentFlagService(@Value("${orchestrai.planning.v2-enabled:false}") final boolean v2Enabled) {
        this.v2Enabled = v2Enabled;
    }

    @Override
    public boolean isEnabled(final String flagName) {
        if ("planning-governance-rollout".equals(flagName)) {
            return v2Enabled;
        }
        return false;
    }

    @Override
    public String policyVersion() {
        return v2Enabled ? "experiment-property-v2" : "experiment-property-v1";
    }
}
