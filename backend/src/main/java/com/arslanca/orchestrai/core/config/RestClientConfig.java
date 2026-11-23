package com.arslanca.orchestrai.core.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class RestClientConfig {

    private static final Logger LOGGER = LoggerFactory.getLogger(RestClientConfig.class);
    private static final Duration LOCAL_TEST_LIGHT_AI_MIN_READ_TIMEOUT = Duration.ofSeconds(90);

    private final RestClientTimeoutProperties properties;
    private final AiProviderRuntimeProperties aiProviderRuntimeProperties;

    public RestClientConfig(
            final RestClientTimeoutProperties properties,
            final AiProviderRuntimeProperties aiProviderRuntimeProperties) {
        this.properties = properties;
        this.aiProviderRuntimeProperties = aiProviderRuntimeProperties;
    }

    @Bean
    public RestClient.Builder utilityRestClientBuilder() {
        return build(properties.getUtility());
    }

    @Bean
    public RestClient.Builder lightAiRestClientBuilder() {
        return build(resolveLightAiProfile());
    }

    @Bean
    public RestClient.Builder heavyAiRestClientBuilder() {
        return build(properties.getHeavyAi());
    }

    @Bean
    public RestClient.Builder workerRestClientBuilder() {
        return build(properties.getWorker());
    }

    private RestClientTimeoutProperties.ClientTimeoutProfile resolveLightAiProfile() {
        final RestClientTimeoutProperties.ClientTimeoutProfile configuredProfile = properties.getLightAi();
        if (!aiProviderRuntimeProperties.testModeEnabled()
                || !aiProviderRuntimeProperties.isLocalBaseUrl("openai-compatible")) {
            return configuredProfile;
        }

        final Duration configuredConnectTimeout = configuredProfile.getConnectTimeout() == null
                ? Duration.ofSeconds(5)
                : configuredProfile.getConnectTimeout();
        final Duration configuredReadTimeout = configuredProfile.getReadTimeout() == null
                ? LOCAL_TEST_LIGHT_AI_MIN_READ_TIMEOUT
                : configuredProfile.getReadTimeout();
        final Duration effectiveReadTimeout = configuredReadTimeout.compareTo(LOCAL_TEST_LIGHT_AI_MIN_READ_TIMEOUT) >= 0
                ? configuredReadTimeout
                : LOCAL_TEST_LIGHT_AI_MIN_READ_TIMEOUT;

        if (!effectiveReadTimeout.equals(configuredReadTimeout)) {
            LOGGER.info(
                    "Increasing local/test light AI read timeout from {} to {} for local openai-compatible routing",
                    configuredReadTimeout,
                    effectiveReadTimeout);
        }

        return new RestClientTimeoutProperties.ClientTimeoutProfile(configuredConnectTimeout, effectiveReadTimeout);
    }

    private RestClient.Builder build(final RestClientTimeoutProperties.ClientTimeoutProfile profile) {
        final Duration connectTimeout = profile.getConnectTimeout();
        final Duration readTimeout = profile.getReadTimeout();

        final HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connectTimeout)
                .build();

        final JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);

        return RestClient.builder().requestFactory(requestFactory);
    }
}
