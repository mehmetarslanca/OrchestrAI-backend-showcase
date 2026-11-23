package com.arslanca.orchestrai.business.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record GitHubTokenRequest(
        @NotBlank(message = "GitHub authorization code boş olamaz") String code) {
}
