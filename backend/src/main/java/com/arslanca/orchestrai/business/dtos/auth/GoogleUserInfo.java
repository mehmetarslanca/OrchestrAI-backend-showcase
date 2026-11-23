package com.arslanca.orchestrai.business.dtos.auth;

public record GoogleUserInfo(
        String sub,
        String email,
        String name,
        String picture) {
}
