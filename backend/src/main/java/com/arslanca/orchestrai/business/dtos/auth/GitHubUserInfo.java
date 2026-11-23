package com.arslanca.orchestrai.business.dtos.auth;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubUserInfo(
        Long id,
        String login,
        String email,

        @JsonProperty("avatar_url") String avatarUrl) {
}
