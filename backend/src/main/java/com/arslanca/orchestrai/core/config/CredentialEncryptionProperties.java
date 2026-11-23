package com.arslanca.orchestrai.core.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "orchestrai.security.credentials")
public record CredentialEncryptionProperties(
        @NotBlank(message = "LLM credentials master key must be configured")
        String masterKey,
        @Min(value = 1, message = "Credential key version must be at least 1")
        int currentKeyVersion) {
}
