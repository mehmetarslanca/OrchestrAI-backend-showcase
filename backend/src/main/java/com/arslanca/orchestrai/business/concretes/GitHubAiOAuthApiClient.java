package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.auth.GitHubTokenResponse;
import com.arslanca.orchestrai.business.dtos.auth.GitHubUserInfo;
import com.arslanca.orchestrai.core.config.GitHubAiOAuthProperties;
import com.arslanca.orchestrai.core.exception.type.GitHubAuthenticationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Component
public class GitHubAiOAuthApiClient {

    private final GitHubAiOAuthProperties gitHubAiOAuthProperties;
    private final RestClient.Builder utilityRestClientBuilder;

    public GitHubAiOAuthApiClient(
            final GitHubAiOAuthProperties gitHubAiOAuthProperties,
            final RestClient.Builder utilityRestClientBuilder) {
        this.gitHubAiOAuthProperties = gitHubAiOAuthProperties;
        this.utilityRestClientBuilder = utilityRestClientBuilder;
    }

    public String buildAuthorizationUrl(final GitHubAiConnectionProduct product, final String stateToken) {
        return UriComponentsBuilder.fromUriString(gitHubAiOAuthProperties.authorizeUri())
                .queryParam("client_id", gitHubAiOAuthProperties.clientId())
                .queryParam("redirect_uri", gitHubAiOAuthProperties.redirectUri())
                .queryParam("scope", String.join(" ", product.scopes(gitHubAiOAuthProperties)))
                .queryParam("state", stateToken)
                .build(true)
                .toUriString();
    }

    public GitHubTokenResponse exchangeCodeForToken(final String authorizationCode) {
        try {
            final RestClient restClient = utilityRestClientBuilder.build();
            final GitHubTokenResponse tokenResponse = restClient.post()
                    .uri(gitHubAiOAuthProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new GitHubAiTokenExchangeBody(
                            gitHubAiOAuthProperties.clientId(),
                            gitHubAiOAuthProperties.clientSecret(),
                            authorizationCode,
                            gitHubAiOAuthProperties.redirectUri()))
                    .retrieve()
                    .body(GitHubTokenResponse.class);
            if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new GitHubAuthenticationException("GitHub AI token exchange bos dondu.");
            }
            return tokenResponse;
        } catch (final RestClientException ex) {
            throw new GitHubAuthenticationException("GitHub AI token exchange basarisiz oldu.", ex);
        }
    }

    public GitHubTokenResponse refreshAccessToken(final String refreshToken) {
        try {
            final RestClient restClient = utilityRestClientBuilder.build();
            final GitHubTokenResponse tokenResponse = restClient.post()
                    .uri(gitHubAiOAuthProperties.tokenUri())
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(new GitHubAiRefreshTokenBody(
                            gitHubAiOAuthProperties.clientId(),
                            gitHubAiOAuthProperties.clientSecret(),
                            refreshToken,
                            "refresh_token"))
                    .retrieve()
                    .body(GitHubTokenResponse.class);
            if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new GitHubAuthenticationException("GitHub AI refresh token exchange bos dondu.");
            }
            return tokenResponse;
        } catch (final RestClientException ex) {
            throw new GitHubAuthenticationException("GitHub AI refresh token exchange basarisiz oldu.", ex);
        }
    }

    public GitHubUserInfo fetchUserInfo(final String accessToken) {
        try {
            final RestClient restClient = utilityRestClientBuilder.build();
            final GitHubUserInfo userInfo = restClient.get()
                    .uri(gitHubAiOAuthProperties.userApiUri())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GitHubUserInfo.class);
            if (userInfo == null || userInfo.id() == null) {
                throw new GitHubAuthenticationException("GitHub AI account bilgisi bos dondu.");
            }
            return userInfo;
        } catch (final RestClientException ex) {
            throw new GitHubAuthenticationException("GitHub AI account bilgisi alinamadi.", ex);
        }
    }

    public void ensureConfigured() {
        if (!gitHubAiOAuthProperties.configured()) {
            throw new IllegalStateException("GitHub AI OAuth bu ortamda konfigure edilmemis.");
        }
    }

    public List<String> scopesFor(final GitHubAiConnectionProduct product) {
        return product.scopes(gitHubAiOAuthProperties);
    }

    public String redirectUri() {
        return gitHubAiOAuthProperties.redirectUri();
    }

    private record GitHubAiTokenExchangeBody(
            String client_id,
            String client_secret,
            String code,
            String redirect_uri) {
    }

    private record GitHubAiRefreshTokenBody(
            String client_id,
            String client_secret,
            String refresh_token,
            String grant_type) {
    }
}
