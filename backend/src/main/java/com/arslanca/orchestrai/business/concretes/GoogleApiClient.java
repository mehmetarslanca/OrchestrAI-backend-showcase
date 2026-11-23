package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.auth.GoogleTokenResponse;
import com.arslanca.orchestrai.business.dtos.auth.GoogleUserInfo;
import com.arslanca.orchestrai.core.config.GoogleOAuthProperties;
import com.arslanca.orchestrai.core.exception.type.GoogleAuthenticationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class GoogleApiClient {

    private static final String GOOGLE_TOKEN_URL = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_USERINFO_URL = "https://openidconnect.googleapis.com/v1/userinfo";

    private final GoogleOAuthProperties googleOAuthProperties;
    private final RestClient.Builder utilityRestClientBuilder;

    public GoogleApiClient(
            final GoogleOAuthProperties googleOAuthProperties,
            final RestClient.Builder utilityRestClientBuilder) {
        this.googleOAuthProperties = googleOAuthProperties;
        this.utilityRestClientBuilder = utilityRestClientBuilder;
    }

    public GoogleTokenResponse exchangeCodeForToken(final String authorizationCode) {
        if (!googleOAuthProperties.configured()) {
            throw new GoogleAuthenticationException("Google OAuth is not configured for this environment.");
        }

        try {
            final RestClient restClient = utilityRestClientBuilder.build();
            final MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
            body.add("code", authorizationCode);
            body.add("client_id", googleOAuthProperties.clientId());
            body.add("client_secret", googleOAuthProperties.clientSecret());
            body.add("redirect_uri", googleOAuthProperties.redirectUri());
            body.add("grant_type", "authorization_code");

            final GoogleTokenResponse tokenResponse = restClient.post()
                    .uri(GOOGLE_TOKEN_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(GoogleTokenResponse.class);

            if (tokenResponse == null || tokenResponse.accessToken() == null || tokenResponse.accessToken().isBlank()) {
                throw new GoogleAuthenticationException(
                        "Google token exchange returned empty response. Authorization code may be invalid or expired.");
            }

            return tokenResponse;
        } catch (final RestClientException ex) {
            log.error("Google token exchange failed", ex);
            throw new GoogleAuthenticationException("Google API unreachable during token exchange.", ex);
        }
    }

    public GoogleUserInfo fetchUserInfo(final String accessToken) {
        try {
            final RestClient restClient = utilityRestClientBuilder.build();
            final GoogleUserInfo userInfo = restClient.get()
                    .uri(GOOGLE_USERINFO_URL)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(GoogleUserInfo.class);

            if (userInfo == null || userInfo.sub() == null || userInfo.sub().isBlank()) {
                throw new GoogleAuthenticationException(
                        "Google user info response is empty or missing required fields.");
            }

            return userInfo;
        } catch (final RestClientException ex) {
            log.error("Google user info fetch failed", ex);
            throw new GoogleAuthenticationException("Google API unreachable while fetching user info.", ex);
        }
    }
}
