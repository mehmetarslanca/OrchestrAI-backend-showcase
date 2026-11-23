package com.arslanca.orchestrai.core.config.projectgeneration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "project-generation.features")
public class ProjectGenerationFeatureProperties {
    private boolean prepareEnabled = true;
    private boolean workerEnabled = true;
    private boolean officialSpringInitializrEnabled = true;
    private boolean chatPrepareEnabled = false;
    private boolean docsOnlyFallbackEnabled = true;
    private boolean legacyGenerateEnabled = true;

    public boolean isPrepareEnabled() { return prepareEnabled; }
    public void setPrepareEnabled(boolean prepareEnabled) { this.prepareEnabled = prepareEnabled; }

    public boolean isWorkerEnabled() { return workerEnabled; }
    public void setWorkerEnabled(boolean workerEnabled) { this.workerEnabled = workerEnabled; }

    public boolean isOfficialSpringInitializrEnabled() { return officialSpringInitializrEnabled; }
    public void setOfficialSpringInitializrEnabled(boolean officialSpringInitializrEnabled) { this.officialSpringInitializrEnabled = officialSpringInitializrEnabled; }

    public boolean isChatPrepareEnabled() { return chatPrepareEnabled; }
    public void setChatPrepareEnabled(boolean chatPrepareEnabled) { this.chatPrepareEnabled = chatPrepareEnabled; }

    public boolean isDocsOnlyFallbackEnabled() { return docsOnlyFallbackEnabled; }
    public void setDocsOnlyFallbackEnabled(boolean docsOnlyFallbackEnabled) { this.docsOnlyFallbackEnabled = docsOnlyFallbackEnabled; }

    public boolean isLegacyGenerateEnabled() { return legacyGenerateEnabled; }
    public void setLegacyGenerateEnabled(boolean legacyGenerateEnabled) { this.legacyGenerateEnabled = legacyGenerateEnabled; }
}
