package com.arslanca.orchestrai.business.dtos.auth;

import jakarta.validation.constraints.NotBlank;

public record GoogleTokenRequest(
        @NotBlank(message = "Google authorization code bos olamaz") String code) {
}
