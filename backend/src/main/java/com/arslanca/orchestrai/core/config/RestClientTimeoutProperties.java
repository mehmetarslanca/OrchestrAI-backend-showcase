package com.arslanca.orchestrai.core.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "orchestrai.http.clients")
public class RestClientTimeoutProperties {

    private ClientTimeoutProfile utility = new ClientTimeoutProfile(Duration.ofSeconds(5), Duration.ofSeconds(10));
    private ClientTimeoutProfile lightAi = new ClientTimeoutProfile(Duration.ofSeconds(5), Duration.ofSeconds(20));
    private ClientTimeoutProfile heavyAi = new ClientTimeoutProfile(Duration.ofSeconds(10), Duration.ofSeconds(300));
    private ClientTimeoutProfile worker = new ClientTimeoutProfile(Duration.ofSeconds(5), Duration.ofSeconds(120));

    public ClientTimeoutProfile getUtility() {
        return utility;
    }

    public void setUtility(final ClientTimeoutProfile utility) {
        this.utility = utility;
    }

    public ClientTimeoutProfile getLightAi() {
        return lightAi;
    }

    public void setLightAi(final ClientTimeoutProfile lightAi) {
        this.lightAi = lightAi;
    }

    public ClientTimeoutProfile getHeavyAi() {
        return heavyAi;
    }

    public void setHeavyAi(final ClientTimeoutProfile heavyAi) {
        this.heavyAi = heavyAi;
    }

    public ClientTimeoutProfile getWorker() {
        return worker;
    }

    public void setWorker(final ClientTimeoutProfile worker) {
        this.worker = worker;
    }

    public static class ClientTimeoutProfile {

        private Duration connectTimeout;
        private Duration readTimeout;

        public ClientTimeoutProfile() {
        }

        public ClientTimeoutProfile(final Duration connectTimeout, final Duration readTimeout) {
            this.connectTimeout = connectTimeout;
            this.readTimeout = readTimeout;
        }

        public Duration getConnectTimeout() {
            return connectTimeout;
        }

        public void setConnectTimeout(final Duration connectTimeout) {
            this.connectTimeout = connectTimeout;
        }

        public Duration getReadTimeout() {
            return readTimeout;
        }

        public void setReadTimeout(final Duration readTimeout) {
            this.readTimeout = readTimeout;
        }
    }
}
