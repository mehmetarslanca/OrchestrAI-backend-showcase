package com.arslanca.orchestrai.business.planning.governance;

public interface ExperimentFlagService {
    boolean isEnabled(String flagName);

    String policyVersion();
}
