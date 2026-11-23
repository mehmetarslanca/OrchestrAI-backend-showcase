package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.model.ChatModel;

import com.arslanca.orchestrai.business.abstracts.StarterCatalogService;
import com.arslanca.orchestrai.business.dtos.orchestration.StructuredAnalysis;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCatalogStatus;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterManifest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;

@ExtendWith(MockitoExtension.class)
class StarterResolutionManagerTest {

  @Mock
  private StarterCatalogService starterCatalogService;

  @Mock
  private ChatModel lightChatModel;

  private StarterResolutionManager starterResolutionManager;

  @BeforeEach
  void setUp() {
    starterResolutionManager = new StarterResolutionManager(starterCatalogService, lightChatModel);
  }

  @Test
  void shouldReturnDocsOnlyFallbackWhenNoStarterMeetsThreshold() {
    when(starterCatalogService.getApprovedStarters()).thenReturn(List.of(
        manifest("react-vite-dashboard", "react", List.of("web-app"), List.of("react", "vite"), 70),
        manifest("nextjs-saas-shell", "nextjs", List.of("saas"), List.of("nextjs", "react", "typescript"), 80)));

    final StarterSelection selection = starterResolutionManager.resolveStarter(
        analysis("COBOL Payroll", "cli-tool", List.of("cobol"), List.of("legacy modernization"), "unsupported stack"),
        "cursor");

    assertThat(selection.docsOnlyFallback()).isTrue();
    assertThat(selection.docsOnlyFallbackReason()).contains("architecture-only blueprint generation");
    assertThat(selection.recommendedStarter()).isNull();
  }

  @Test
  void shouldReturnRecommendedStarterAndUniqueAlternativesDeterministically() {
    when(starterCatalogService.getApprovedStarters()).thenReturn(List.of(
        manifest("nextjs-saas-shell", "nextjs", List.of("saas", "web-app"),
            List.of("nextjs", "react", "typescript", "stripe"), 100),
        manifest("nextjs-dashboard-starter", "nextjs", List.of("dashboard", "web-app"),
            List.of("nextjs", "react", "typescript", "auth"), 92),
        manifest("react-vite-dashboard", "react", List.of("dashboard", "web-app"),
            List.of("react", "vite", "typescript"), 85)));

    final StarterSelection selection = starterResolutionManager.resolveStarter(
        analysis("Acme SaaS", "saas", List.of("nextjs", "typescript", "stripe"), List.of("dashboard", "billing"),
            "saas dashboard"),
        "cursor");

    assertThat(selection.docsOnlyFallback()).isFalse();
    assertThat(selection.recommendedStarter().key()).isEqualTo("nextjs-saas-shell");
    assertThat(selection.alternatives()).extracting(candidate -> candidate.key())
        .containsExactly("nextjs-dashboard-starter", "react-vite-dashboard");
    assertThat(selection.alternatives()).extracting(candidate -> candidate.key())
        .doesNotContain(selection.recommendedStarter().key());
  }

  @Test
  void shouldPreferHigherPriorityWhenScoresTie() {
    when(starterCatalogService.getApprovedStarters()).thenReturn(List.of(
        manifest("spring-boot-auth-service", "spring-boot", List.of("api"),
            List.of("spring-boot", "java21", "postgres"), 95),
        manifest("spring-boot-postgres-api", "spring-boot", List.of("api"),
            List.of("spring-boot", "java21", "postgres"), 100)));
    when(lightChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("timeout"));

    final StarterSelection selection = starterResolutionManager.resolveStarter(
        analysis("Account API", "api", List.of("spring boot", "java21", "postgres"), List.of("authentication"),
            "secure api"),
        "cursor");

    assertThat(selection.recommendedStarter().key()).isEqualTo("spring-boot-postgres-api");
  }

  @Test
  void shouldFallBackToDeterministicReasonWhenAiExplanationFails() {
    when(starterCatalogService.getApprovedStarters()).thenReturn(List.of(
        manifest("nextjs-saas-shell", "platform", List.of("web-app"), List.of("typescript"), 100),
        manifest("react-vite-dashboard", "platform", List.of("web-app"), List.of("typescript"), 95)));
    when(lightChatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("timeout"));

    final StarterSelection selection = starterResolutionManager.resolveStarter(
        analysis("Platform", "web-app", List.of("typescript"), List.of("auth"), "dashboard app"),
        "cursor");

    assertThat(selection.docsOnlyFallback()).isFalse();
    assertThat(selection.recommendedStarter().reason()).contains("Matches");
    verify(lightChatModel).call(any(Prompt.class));
  }

  @Test
  void shouldIgnoreDisabledStartersEvenIfCatalogServiceReturnsThem() {
    when(starterCatalogService.getApprovedStarters()).thenReturn(List.of(
        manifest("nextjs-saas-shell", "nextjs", List.of("saas"), List.of("nextjs", "typescript"), 100,
            StarterCatalogStatus.DISABLED),
        manifest("spring-boot-postgres-api", "spring-boot", List.of("api"),
            List.of("spring-boot", "java21", "postgres"), 90, StarterCatalogStatus.ACTIVE)));

    final StarterSelection selection = starterResolutionManager.resolveStarter(
        analysis("Payments API", "api", List.of("spring-boot", "java21", "postgres"), List.of("auth"),
            "backend api"),
        "cursor");

    assertThat(selection.docsOnlyFallback()).isFalse();
    assertThat(selection.recommendedStarter().key()).isEqualTo("spring-boot-postgres-api");
  }

  private StructuredAnalysis analysis(
      final String projectName,
      final String projectType,
      final List<String> techStack,
      final List<String> features,
      final String summary) {
    return new StructuredAnalysis(projectName, projectType, techStack, features, "moderate", false, null, summary);
  }

  private StarterManifest manifest(
      final String key,
      final String family,
      final List<String> projectTypes,
      final List<String> stackTags,
      final int priority) {
    return manifest(key, family, projectTypes, stackTags, priority, StarterCatalogStatus.ACTIVE);
  }

  private StarterManifest manifest(
      final String key,
      final String family,
      final List<String> projectTypes,
      final List<String> stackTags,
      final int priority,
      final StarterCatalogStatus status) {
    return new StarterManifest(
        key,
        key,
        family,
        projectTypes,
        stackTags,
        List.of("cursor", "generic"),
        status,
        StarterSourceKind.REPO_PACK,
        key,
        priority,
        true,
        java.util.Map.of(),
        List.of());
  }
}
