package com.arslanca.orchestrai.business.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record RefreshTokenRequest(
        @NotBlank(message = "Refresh token bos olamaz") String refreshToken) {
}
