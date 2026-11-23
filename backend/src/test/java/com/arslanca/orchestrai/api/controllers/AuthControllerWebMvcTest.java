package com.arslanca.orchestrai.api.controllers;

import com.arslanca.orchestrai.business.abstracts.AuthService;
import com.arslanca.orchestrai.business.dtos.auth.AuthResponse;
import com.arslanca.orchestrai.business.dtos.user.UserResponse;
import com.arslanca.orchestrai.core.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.OffsetDateTime;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerWebMvcTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockMvc mockMvc;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    @Test
    void shouldAuthenticateWithGitHubCode() throws Exception {
        AuthResponse response = AuthResponse.of(
                "jwt-token",
                86400000,
                new UserResponse(
                        UUID.randomUUID(),
                        "memet",
                        "memet@orchestr.ai",
                        "https://avatar",
                        "GITHUB",
                        "USER",
                        OffsetDateTime.now()));

        when(authService.authenticateWithGitHub("valid-code")).thenReturn(response);

        mockMvc.perform(post("/api/v1/auth/github/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("code", "valid-code"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void shouldReturnValidationErrorForBlankCode() throws Exception {
        mockMvc.perform(post("/api/v1/auth/github/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(java.util.Map.of("code", ""))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_ERROR"));
    }
}
