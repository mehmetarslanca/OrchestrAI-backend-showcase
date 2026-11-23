package com.arslanca.orchestrai.integration.redis;

import com.arslanca.orchestrai.business.abstracts.RateLimitService;
import com.arslanca.orchestrai.core.config.RateLimitInterceptor;
import com.arslanca.orchestrai.core.config.projectgeneration.ProjectGenerationFeatureProperties;
import com.arslanca.orchestrai.core.exception.ApiErrorResponseWriter;
import com.arslanca.orchestrai.core.exception.type.InfrastructureUnavailableException;
import com.arslanca.orchestrai.core.idempotency.IdempotencyInterceptor;
import com.arslanca.orchestrai.core.idempotency.IdempotencyStore;
import com.arslanca.orchestrai.core.security.AuthenticatedUser;
import com.arslanca.orchestrai.entity.enums.UserRole;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RedisOutagePolicyIntegrationTest {

  private MockMvc mockMvc;
  private RateLimitService rateLimitService;
  private IdempotencyStore idempotencyStore;
  private ProjectGenerationFeatureProperties featureProperties;

  @BeforeEach
  void setUp() {
    rateLimitService = mock(RateLimitService.class);
    idempotencyStore = mock(IdempotencyStore.class);
    featureProperties = new ProjectGenerationFeatureProperties();
    ApiErrorResponseWriter writer = new ApiErrorResponseWriter(new ObjectMapper());

    mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
        .addInterceptors(
            new IdempotencyInterceptor(idempotencyStore, writer, featureProperties),
            new RateLimitInterceptor(rateLimitService, writer, featureProperties))
        .build();
  }

  @AfterEach
  void clearSecurityContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void shouldFailClosedForProjectGenerationWhenRateLimitRedisIsUnavailable() throws Exception {
    when(idempotencyStore.checkAndMark("idem-1")).thenReturn(true);
    when(rateLimitService.tryAcquire(TestSecurity.USER_ID))
        .thenThrow(new InfrastructureUnavailableException("rate limiter down"));

    mockMvc.perform(post("/api/v1/projects/generate")
        .header("X-Idempotency-Key", "idem-1")
        .with(authenticatedUser()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  void shouldFailClosedForProjectGenerationWithTrailingSlashWhenRateLimitRedisIsUnavailable() throws Exception {
    when(idempotencyStore.checkAndMark("idem-1")).thenReturn(true);
    when(rateLimitService.tryAcquire(TestSecurity.USER_ID))
        .thenThrow(new InfrastructureUnavailableException("rate limiter down"));

    mockMvc.perform(post("/api/v1/projects/generate/")
        .header("X-Idempotency-Key", "idem-1")
        .with(authenticatedUser()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  void shouldFailClosedForCanonicalPrepareWhenRateLimitRedisIsUnavailable() throws Exception {
    when(idempotencyStore.checkAndMark("idem-prepare")).thenReturn(true);
    when(rateLimitService.tryAcquire(TestSecurity.USER_ID))
        .thenThrow(new InfrastructureUnavailableException("rate limiter down"));

    mockMvc.perform(post("/api/v1/project-generation/prepare")
        .header("X-Idempotency-Key", "idem-prepare")
        .with(authenticatedUser()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  void shouldFailClosedForCanonicalStartWhenRateLimitRedisIsUnavailable() throws Exception {
    when(idempotencyStore.checkAndMark("idem-start")).thenReturn(true);
    when(rateLimitService.tryAcquire(TestSecurity.USER_ID))
        .thenThrow(new InfrastructureUnavailableException("rate limiter down"));

    mockMvc.perform(post("/api/v1/project-generation/start")
        .header("X-Idempotency-Key", "idem-start")
        .with(authenticatedUser()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  void shouldFailClosedForChatMessagesWhenRateLimitRedisIsUnavailable() throws Exception {
    when(idempotencyStore.checkAndMark("idem-2")).thenReturn(true);
    when(rateLimitService.tryAcquire(TestSecurity.USER_ID))
        .thenThrow(new InfrastructureUnavailableException("rate limiter down"));

    mockMvc.perform(post("/api/v1/conversations/chats/123-abc/messages")
        .header("X-Idempotency-Key", "idem-2")
        .with(authenticatedUser()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  void shouldFailClosedForChatMessagesWithTrailingSlashWhenRateLimitRedisIsUnavailable() throws Exception {
    when(idempotencyStore.checkAndMark("idem-2")).thenReturn(true);
    when(rateLimitService.tryAcquire(TestSecurity.USER_ID))
        .thenThrow(new InfrastructureUnavailableException("rate limiter down"));

    mockMvc.perform(post("/api/v1/conversations/chats/123-abc/messages/")
        .header("X-Idempotency-Key", "idem-2")
        .with(authenticatedUser()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  void shouldFailOpenForReadEndpointWhenRateLimitRedisIsUnavailable() throws Exception {
    when(rateLimitService.tryAcquire(TestSecurity.USER_ID))
        .thenThrow(new InfrastructureUnavailableException("rate limiter down"));

    mockMvc.perform(get("/api/v1/projects").with(authenticatedUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  @Test
  void shouldFailClosedForProjectGenerationWhenIdempotencyRedisIsUnavailable() throws Exception {
    when(idempotencyStore.checkAndMark("idem-1"))
        .thenThrow(new InfrastructureUnavailableException("idempotency down"));

    mockMvc.perform(post("/api/v1/projects/generate")
        .header("X-Idempotency-Key", "idem-1")
        .with(authenticatedUser()))
        .andExpect(status().isServiceUnavailable())
        .andExpect(jsonPath("$.error").value("SERVICE_UNAVAILABLE"));
  }

  @Test
  void shouldBypassInterceptorsForDisabledLegacyEndpoint() throws Exception {
    featureProperties.setLegacyGenerateEnabled(false);

    mockMvc.perform(post("/api/v1/projects/generate")
        .with(authenticatedUser()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("ok"));
  }

  private RequestPostProcessor authenticatedUser() {
    return request -> {
      SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
          new AuthenticatedUser(TestSecurity.USER_ID, "memet", UserRole.USER, null),
          null,
          List.of(new SimpleGrantedAuthority("ROLE_USER"))));
      return request;
    };
  }

  @RestController
  static class TestController {

    @PostMapping(value = { "/api/v1/projects/generate", "/api/v1/projects/generate/" })
    ResponseEntity<java.util.Map<String, String>> generate() {
      return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }

    @PostMapping(value = { "/api/v1/project-generation/prepare", "/api/v1/project-generation/prepare/" })
    ResponseEntity<java.util.Map<String, String>> prepare() {
      return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }

    @PostMapping(value = { "/api/v1/project-generation/start", "/api/v1/project-generation/start/" })
    ResponseEntity<java.util.Map<String, String>> start() {
      return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }

    @PostMapping(value = { "/api/v1/conversations/chats/{chatId}/messages",
        "/api/v1/conversations/chats/{chatId}/messages/" })
    ResponseEntity<java.util.Map<String, String>> chatMessages() {
      return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }

    @GetMapping("/api/v1/projects")
    ResponseEntity<java.util.Map<String, String>> list() {
      return ResponseEntity.ok(java.util.Map.of("status", "ok"));
    }
  }

  static final class TestSecurity {
    private static final UUID USER_ID = UUID.randomUUID();

    private TestSecurity() {
    }
  }
}
