package com.arslanca.orchestrai.core.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "orchestrai.pipeline.recovery")
public class PipelineRecoveryProperties {
    private int stalePendingMinutes = 2;
    private int staleProcessingMinutes = 30;

    public int getStalePendingMinutes() {
        return stalePendingMinutes;
    }

    public void setStalePendingMinutes(final int stalePendingMinutes) {
        this.stalePendingMinutes = stalePendingMinutes;
    }

    public int getStaleProcessingMinutes() {
        return staleProcessingMinutes;
    }

    public void setStaleProcessingMinutes(final int staleProcessingMinutes) {
        this.staleProcessingMinutes = staleProcessingMinutes;
    }
}
