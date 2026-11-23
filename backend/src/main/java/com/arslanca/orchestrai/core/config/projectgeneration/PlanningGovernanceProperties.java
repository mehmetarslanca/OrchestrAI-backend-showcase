package com.arslanca.orchestrai.core.config.projectgeneration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "orchestrai.planning.governance")
public class PlanningGovernanceProperties {

    private String rolloutMode = "off";
    private String minimumComplexity = "complex";
    private int rolloutPercentage = 100;
    private boolean includeLocalModels = false;
    private boolean killSwitch = false;
    private long reviewReadyReuseTtlSeconds = 21600L;
    private long acceptedPreviewReuseTtlSeconds = 86400L;

    public String getRolloutMode() {
        return rolloutMode;
    }

    public void setRolloutMode(final String rolloutMode) {
        this.rolloutMode = rolloutMode == null ? "off" : rolloutMode.trim();
    }

    public String getMinimumComplexity() {
        return minimumComplexity;
    }

    public void setMinimumComplexity(final String minimumComplexity) {
        this.minimumComplexity = minimumComplexity == null ? "complex" : minimumComplexity.trim();
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(final int rolloutPercentage) {
        this.rolloutPercentage = Math.max(0, Math.min(100, rolloutPercentage));
    }

    public boolean isIncludeLocalModels() {
        return includeLocalModels;
    }

    public void setIncludeLocalModels(final boolean includeLocalModels) {
        this.includeLocalModels = includeLocalModels;
    }

    public boolean isKillSwitch() {
        return killSwitch;
    }

    public void setKillSwitch(final boolean killSwitch) {
        this.killSwitch = killSwitch;
    }

    public long getReviewReadyReuseTtlSeconds() {
        return reviewReadyReuseTtlSeconds;
    }

    public void setReviewReadyReuseTtlSeconds(final long reviewReadyReuseTtlSeconds) {
        this.reviewReadyReuseTtlSeconds = Math.max(0L, reviewReadyReuseTtlSeconds);
    }

    public long getAcceptedPreviewReuseTtlSeconds() {
        return acceptedPreviewReuseTtlSeconds;
    }

    public void setAcceptedPreviewReuseTtlSeconds(final long acceptedPreviewReuseTtlSeconds) {
        this.acceptedPreviewReuseTtlSeconds = Math.max(0L, acceptedPreviewReuseTtlSeconds);
    }
}
