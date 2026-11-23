package com.arslanca.orchestrai.core.ai;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpTimeoutException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

class ProviderFailureClassifierTest {

    @Test
    @DisplayName("Should classify nested request cancelled timeout as network failure")
    void shouldClassifyNestedRequestCancelledTimeoutAsNetworkFailure() {
        final Throwable error = new ResourceAccessException(
                "I/O error on POST request for \"http://localhost:4000/v1/chat/completions\": Request cancelled",
                new HttpTimeoutException("Request cancelled"));

        assertThat(ProviderFailureClassifier.classify(error))
                .isEqualTo(ProviderFailureClassifier.FailureType.NETWORK_FAILURE);
    }
}
