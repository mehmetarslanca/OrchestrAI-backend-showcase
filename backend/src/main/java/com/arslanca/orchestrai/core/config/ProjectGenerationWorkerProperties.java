package com.arslanca.orchestrai.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrai.project-generation.worker")
public class ProjectGenerationWorkerProperties {

    private String baseUrl = "http://localhost:8081";
    private String internalSecret = "";
    private String repoPacksDir = "../starter-packs";

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public String getInternalSecret() {
        return internalSecret;
    }

    public void setInternalSecret(final String internalSecret) {
        this.internalSecret = internalSecret;
    }

    public boolean hasConfiguredInternalSecret() {
        return internalSecret != null
                && !internalSecret.isBlank()
                && !"change-me".equalsIgnoreCase(internalSecret.trim());
    }

    public String getRepoPacksDir() {
        return repoPacksDir;
    }

    public void setRepoPacksDir(final String repoPacksDir) {
        this.repoPacksDir = repoPacksDir;
    }
}
