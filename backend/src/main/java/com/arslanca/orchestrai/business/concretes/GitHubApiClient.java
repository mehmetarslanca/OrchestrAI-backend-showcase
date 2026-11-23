package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.core.config.GitHubOAuthProperties;
import com.arslanca.orchestrai.core.exception.type.GitHubAuthenticationException;
import com.arslanca.orchestrai.business.dtos.auth.GitHubTokenResponse;
import com.arslanca.orchestrai.business.dtos.auth.GitHubUserInfo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class GitHubApiClient {

        private static final String GITHUB_TOKEN_URL = "https://github.com/login/oauth/access_token";
        private static final String GITHUB_USER_URL = "https://api.github.com/user";

        private final GitHubOAuthProperties githubProperties;

    private final RestClient.Builder utilityRestClientBuilder;

    public GitHubApiClient(GitHubOAuthProperties githubProperties, RestClient.Builder utilityRestClientBuilder) {
        this.githubProperties = githubProperties;
        this.utilityRestClientBuilder = utilityRestClientBuilder;
    }

        /**
         * GitHub authorization code'u access_token'a çevirir.
         */
        public GitHubTokenResponse exchangeCodeForToken(final String authorizationCode) {
                try {
                        final RestClient restClient = utilityRestClientBuilder.build();

                        final GitHubTokenResponse tokenResponse = restClient.post()
                                        .uri(GITHUB_TOKEN_URL)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .body(new TokenExchangeBody(
                                                        githubProperties.clientId(),
                                                        githubProperties.clientSecret(),
                                                        authorizationCode))
                                        .retrieve()
                                        .body(GitHubTokenResponse.class);

                        if (tokenResponse == null || tokenResponse.accessToken() == null) {
                                throw new GitHubAuthenticationException(
                                                "GitHub token exchange returned empty response. Authorization code may be invalid or expired.");
                        }

                        return tokenResponse;
                } catch (RestClientException ex) {
                        log.error("GitHub token exchange failed", ex);
                        throw new GitHubAuthenticationException(
                                        "GitHub API unreachable during token exchange.", ex);
                }
        }

        /**
         * GitHub access_token ile kullanıcı bilgilerini çeker.
         */
        public GitHubUserInfo fetchUserInfo(final String accessToken) {
                try {
                        final RestClient restClient = utilityRestClientBuilder.build();

                        final GitHubUserInfo userInfo = restClient.get()
                                        .uri(GITHUB_USER_URL)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                        .accept(MediaType.APPLICATION_JSON)
                                        .retrieve()
                                        .body(GitHubUserInfo.class);

                        if (userInfo == null || userInfo.id() == null) {
                                throw new GitHubAuthenticationException(
                                                "GitHub user info response is empty or missing required fields.");
                        }

                        return userInfo;
                } catch (RestClientException ex) {
                        log.error("GitHub user info fetch failed", ex);
                        throw new GitHubAuthenticationException(
                                        "GitHub API unreachable while fetching user info.", ex);
                }
        }

        private record TokenExchangeBody(
                        String client_id,
                        String client_secret,
                        String code) {
        }
}
