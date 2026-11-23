package com.arslanca.orchestrai.business.concretes;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.arslanca.orchestrai.business.abstracts.StarterCatalogService;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterCatalogStatus;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterManifest;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSelection;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind;
import com.arslanca.orchestrai.business.enums.GenerationFailureReason;
import com.arslanca.orchestrai.core.config.ProjectGenerationWorkerProperties;
import com.arslanca.orchestrai.core.config.projectgeneration.ProjectGenerationFeatureProperties;
import com.arslanca.orchestrai.core.exception.type.ProjectGenerationCancelledException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;

class ScaffoldMaterializationManagerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private WireMockServer wireMockServer;
    private ProjectGenerationFeatureProperties featureProperties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        wireMockServer = new WireMockServer(options().dynamicPort());
        wireMockServer.start();
        featureProperties = new ProjectGenerationFeatureProperties();
        featureProperties.setWorkerEnabled(true);
        featureProperties.setOfficialSpringInitializrEnabled(true);
        featureProperties.setDocsOnlyFallbackEnabled(true);
        meterRegistry = new SimpleMeterRegistry();
    }

    @AfterEach
    void tearDown() {
        wireMockServer.stop();
    }

    @Test
    void shouldReturnMaterializedStarterWhenWorkerSucceeds() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/materialize"))
                .withHeader(ScaffoldMaterializationManager.INTERNAL_SECRET_HEADER, equalTo("test-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(Map.of(
                                "starterKey", "spring-boot-postgres-api",
                                "sourceKind", "official-adapter",
                                "sourceRef", "spring-initializr",
                                "tree", List.of(Map.of(
                                        "path", "pom.xml",
                                        "content", "<project/>",
                                        "contentEncoding", "utf-8",
                                        "sizeBytes", 10,
                                        "binary", false)),
                                "metadata", Map.of(
                                        "fileCount", 1,
                                        "totalBytes", 10,
                                        "durationMs", 15,
                                        "warnings", List.of(),
                                        "degraded", false,
                                        "degradeReason", ""))))));

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("spring-boot-postgres-api")).thenReturn(Optional.of(manifest()));

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(starterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isFalse();
        assertThat(result.tree()).hasSize(1);
        assertThat(result.tree().get(0).contentEncoding()).isEqualTo("utf-8");
        assertThat(result.tree().get(0).sizeBytes()).isEqualTo(10L);
        assertThat(result.metadata().fileCount()).isEqualTo(1);
    }

    @Test
    void shouldMaterializeRepoPackViaWorkerWithBinarySafeContract() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/materialize"))
                .withHeader(ScaffoldMaterializationManager.INTERNAL_SECRET_HEADER, equalTo("test-secret"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(Map.of(
                                "starterKey", "react-vite-dashboard",
                                "sourceKind", "repo-pack",
                                "sourceRef", "react-vite-dashboard",
                                "tree", List.of(
                                        Map.of(
                                                "path", "package.json",
                                                "content", "{\"name\":\"demo\"}",
                                                "contentEncoding", "utf-8",
                                                "sizeBytes", 15,
                                                "binary", false),
                                        Map.of(
                                                "path", "assets/logo.png",
                                                "content", "AQIAAw==",
                                                "contentEncoding", "base64",
                                                "sizeBytes", 4,
                                                "binary", true)),
                                "metadata", Map.of(
                                        "fileCount", 2,
                                        "totalBytes", 19,
                                        "durationMs", 12,
                                        "warnings", List.of(),
                                        "degraded", false,
                                        "degradeReason", ""))))));

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("react-vite-dashboard")).thenReturn(Optional.of(repoPackManifest()));

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(repoPackStarterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isFalse();
        assertThat(result.tree()).hasSize(2);
        assertThat(result.tree()).extracting(file -> file.path(), file -> file.contentEncoding(), file -> file.binary())
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("package.json", "utf-8", false),
                        org.assertj.core.groups.Tuple.tuple("assets/logo.png", "base64", true));
    }

    @Test
    void shouldDegradeWhenManifestIsDisabled() {
        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("react-vite-dashboard")).thenReturn(Optional.of(disabledRepoPackManifest()));

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(repoPackStarterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isTrue();
        assertThat(result.metadata().warnings()).singleElement().asString().contains("disabled");
        assertThat(result.metadata().degraded()).isTrue();
        assertThat(result.metadata().degradeReason()).contains("disabled");
    }

    @Test
    void shouldDegradeWhenWorkerReturnsServerError() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/materialize"))
                .willReturn(aResponse().withStatus(500)));

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("spring-boot-postgres-api")).thenReturn(Optional.of(manifest()));

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(starterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isTrue();
        assertThat(result.tree()).isEmpty();
        assertThat(result.metadata().warnings()).isNotEmpty();
    }

    @Test
    void shouldDegradeWhenWorkerReturnsClientError() {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/materialize"))
                .willReturn(aResponse().withStatus(400).withBody("Invalid parameters")));

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("spring-boot-postgres-api")).thenReturn(Optional.of(manifest()));

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(starterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isTrue();
        assertThat(result.tree()).isEmpty();
        assertThat(result.metadata().warnings()).isNotEmpty();
    }

    @Test
    void shouldDegradeWhenWorkerConnectionRefusedOrTimeout() {
        // WireMock delay simulates a timeout
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/materialize"))
                .willReturn(aResponse().withFixedDelay(5000).withStatus(200)));

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("spring-boot-postgres-api")).thenReturn(Optional.of(manifest()));

        // Fast timeout to trigger ResourceAccessException
        final SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setReadTimeout(java.time.Duration.ofMillis(100));
        requestFactory.setConnectTimeout(java.time.Duration.ofMillis(100));
        final RestClient.Builder fastTimeoutClient = RestClient.builder().requestFactory(requestFactory);

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                fastTimeoutClient,
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(starterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isTrue();
        assertThat(result.tree()).isEmpty();
        assertThat(result.metadata().warnings()).isNotEmpty();
    }

    @Test
    void shouldDegradeWhenManifestCannotBeResolved() {
        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("spring-boot-postgres-api")).thenReturn(Optional.empty());

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(starterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isTrue();
        assertThat(result.metadata().warnings()).singleElement().asString().contains("manifest not found");
    }

    @Test
    void shouldDegradeWhenWorkerSecretIsMissing() {
        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("spring-boot-postgres-api")).thenReturn(Optional.of(manifest()));

        final ProjectGenerationWorkerProperties properties = workerProperties("");
        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                properties,
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(starterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isTrue();
        assertThat(result.metadata().degradeReason()).contains("internal secret is not configured");
    }

    @Test
    void shouldFailWhenDocsOnlySelectionIsDisabled() {
        featureProperties.setDocsOnlyFallbackEnabled(false);

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        assertThatThrownBy(() -> manager.materialize(docsOnlySelection(), UUID.randomUUID(), () -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Docs-only fallback is disabled");
    }

    @Test
    void shouldFailWhenWorkerDisabledWithoutDocsOnlyFallback() {
        featureProperties.setWorkerEnabled(false);
        featureProperties.setDocsOnlyFallbackEnabled(false);

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        assertThatThrownBy(() -> manager.materialize(starterSelection(), UUID.randomUUID(), () -> { }))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Worker materialization is disabled");
    }

    @Test
    void shouldDegradeWhenSpringInitializrIsDisabled() throws Exception {
        featureProperties.setOfficialSpringInitializrEnabled(false);

        wireMockServer.stubFor(post(urlEqualTo("/api/v1/materialize"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(Map.of(
                                "starterKey", "spring-boot-postgres-api",
                                "sourceKind", "official-adapter",
                                "sourceRef", "spring-initializr",
                                "tree", List.of(),
                                "metadata", Map.of(
                                        "fileCount", 0,
                                        "totalBytes", 0,
                                        "durationMs", 10,
                                        "warnings", List.of(),
                                        "degraded", false,
                                        "degradeReason", ""))))));

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("spring-boot-postgres-api")).thenReturn(Optional.of(manifest()));

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final MaterializedStarter result = manager.materialize(starterSelection(), UUID.randomUUID(), () -> { });

        assertThat(result.degraded()).isTrue();
        assertThat(result.metadata().warnings()).singleElement().asString().contains("Spring Initializr worker is disabled");
    }

    @Test
    void shouldPropagateCancellationAfterWorkerResponseBeforeApplyingResult() throws Exception {
        wireMockServer.stubFor(post(urlEqualTo("/api/v1/materialize"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(objectMapper.writeValueAsString(Map.of(
                                "starterKey", "spring-boot-postgres-api",
                                "sourceKind", "official-adapter",
                                "sourceRef", "spring-initializr",
                                "tree", List.of(Map.of(
                                        "path", "pom.xml",
                                        "content", "<project/>",
                                        "contentEncoding", "utf-8",
                                        "sizeBytes", 10,
                                        "binary", false)),
                                "metadata", Map.of(
                                        "fileCount", 1,
                                        "totalBytes", 10,
                                        "durationMs", 15,
                                        "warnings", List.of(),
                                        "degraded", false,
                                        "degradeReason", ""))))));

        final StarterCatalogService starterCatalogService = mock(StarterCatalogService.class);
        when(starterCatalogService.findByKey("spring-boot-postgres-api")).thenReturn(Optional.of(manifest()));

        final ScaffoldMaterializationManager manager = new ScaffoldMaterializationManager(
                starterCatalogService,
                restClientBuilder(),
                workerProperties("test-secret"),
                featureProperties,
                meterRegistry);

        final UUID projectId = UUID.randomUUID();
        final AtomicInteger probeCount = new AtomicInteger();

        assertThatThrownBy(() -> manager.materialize(
                starterSelection(),
                projectId,
                () -> {
                    if (probeCount.incrementAndGet() > 1) {
                        throw new ProjectGenerationCancelledException(projectId, GenerationFailureReason.USER_CANCELLED);
                    }
                }))
                .isInstanceOf(ProjectGenerationCancelledException.class)
                .hasMessageContaining("USER_CANCELLED");
    }

    private RestClient.Builder restClientBuilder() {
        return RestClient.builder().requestFactory(new SimpleClientHttpRequestFactory());
    }

    private ProjectGenerationWorkerProperties workerProperties(final String secret) {
        final ProjectGenerationWorkerProperties properties = new ProjectGenerationWorkerProperties();
        properties.setBaseUrl(wireMockServer.baseUrl());
        properties.setInternalSecret(secret);
        return properties;
    }

    private StarterManifest manifest() {
        return new StarterManifest(
                "spring-boot-postgres-api",
                "Spring Boot Postgres API",
                "spring-boot",
                List.of("api"),
                List.of("spring-boot", "java21", "postgres", "jpa"),
                List.of("cursor", "generic"),
                StarterCatalogStatus.ACTIVE,
                StarterSourceKind.OFFICIAL_ADAPTER,
                "spring-initializr",
                100,
                true,
                Map.of("build", "maven", "packaging", "jar"),
                List.of());
    }

    private StarterManifest repoPackManifest() {
        return new StarterManifest(
                "react-vite-dashboard",
                "React Vite Dashboard",
                "react",
                List.of("web-app", "dashboard"),
                List.of("react", "vite", "typescript", "tanstack-query"),
                List.of("cursor", "generic"),
                StarterCatalogStatus.ACTIVE,
                StarterSourceKind.REPO_PACK,
                "react-vite-dashboard",
                85,
                true,
                Map.of(),
                List.of());
    }

    private StarterManifest disabledRepoPackManifest() {
        return new StarterManifest(
                "react-vite-dashboard",
                "React Vite Dashboard",
                "react",
                List.of("web-app", "dashboard"),
                List.of("react", "vite", "typescript", "tanstack-query"),
                List.of("cursor", "generic"),
                StarterCatalogStatus.DISABLED,
                StarterSourceKind.REPO_PACK,
                "react-vite-dashboard",
                85,
                true,
                Map.of(),
                List.of());
    }

    private StarterSelection starterSelection() {
        return new StarterSelection(
                UUID.randomUUID(),
                "my-project",
                "cursor",
                manifest().toCandidate("Matches backend api requirements."),
                List.of(),
                false,
                "");
    }

    private StarterSelection repoPackStarterSelection() {
        return new StarterSelection(
                UUID.randomUUID(),
                "my-project",
                "cursor",
                repoPackManifest().toCandidate("Matches dashboard requirements."),
                List.of(),
                false,
                "");
    }

    private StarterSelection docsOnlySelection() {
        return new StarterSelection(
                UUID.randomUUID(),
                "my-project",
                "cursor",
                null,
                List.of(),
                true,
                "No approved starter matched");
    }
}
