package com.arslanca.orchestrai.business.dtos.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ProviderKeyRequest(
        @NotBlank(message = "API key bos olamaz")
        @Size(max = 1000, message = "API key en fazla 1000 karakter olabilir")
        String apiKey) {
}
