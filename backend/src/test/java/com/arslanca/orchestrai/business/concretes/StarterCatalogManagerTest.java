package com.arslanca.orchestrai.business.concretes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

class StarterCatalogManagerTest {

    private final ObjectMapper objectMapper = new ObjectMapper()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void shouldLoadValidFixtureCatalog() {
        final StarterCatalogManager manager = new StarterCatalogManager(
                objectMapper,
                "classpath*:starter-catalog-fixtures/valid/*.json");

        manager.loadCatalog();

        assertThat(manager.getApprovedStarters()).hasSize(2);
        assertThat(manager.findByKey("spring-fixture-api")).isPresent();
    }

    @Test
    void shouldFailFastOnDuplicateManifestKeys() {
        final StarterCatalogManager manager = new StarterCatalogManager(
                objectMapper,
                "classpath*:starter-catalog-fixtures/invalid-duplicate/*.json");

        assertThatThrownBy(manager::loadCatalog)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate starter manifest key detected");
    }

    @Test
    void shouldFailFastOnUnsafeSourceRef() {
        final StarterCatalogManager manager = new StarterCatalogManager(
                objectMapper,
                "classpath*:starter-catalog-fixtures/invalid-source-ref/*.json");

        assertThatThrownBy(manager::loadCatalog)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Invalid starter manifest");
    }

    @Test
    void shouldFailFastOnMalformedJson() {
        final StarterCatalogManager manager = new StarterCatalogManager(
                objectMapper,
                "classpath*:starter-catalog-fixtures/invalid-json/*.json");

        assertThatThrownBy(manager::loadCatalog)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse starter manifest");
    }

    @Test
    void shouldLoadProductionCatalog() {
        final StarterCatalogManager manager = new StarterCatalogManager(objectMapper);

        manager.loadCatalog();

        assertThat(manager.getApprovedStarters()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(manager.getApprovedStarters()).extracting(manifest -> manifest.key())
                .contains("spring-boot-postgres-api", "spring-boot-auth-service")
                .doesNotContain("nextjs-saas-shell", "react-vite-dashboard");
        assertThat(manager.findByKey("spring-boot-postgres-api")).isPresent();
        assertThat(manager.findByKey("nextjs-saas-shell")).isPresent();
    }

    @Test
    void shouldNotExposeDisabledProductionStartersAsApproved() {
        final StarterCatalogManager manager = new StarterCatalogManager(objectMapper);

        manager.loadCatalog();

        assertThat(manager.findByKey("nextjs-saas-shell"))
                .isPresent()
                .get()
                .extracting(manifest -> manifest.status().value())
                .isEqualTo("disabled");
        assertThat(manager.getApprovedStarters()).extracting(manifest -> manifest.key())
                .doesNotContain("nextjs-saas-shell");
    }

    @Test
    void shouldRequireMaterializableContentForActiveRepoPackManifests() throws IOException {
        final StarterCatalogManager manager = new StarterCatalogManager(objectMapper);

        manager.loadCatalog();

        final Path starterPacksRoot = Path.of("..", "starter-packs").toAbsolutePath().normalize();
        assertThat(Files.isDirectory(starterPacksRoot)).isTrue();

        for (final var manifest : manager.getApprovedStarters()) {
            if (manifest.sourceKind() != com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind.REPO_PACK) {
                continue;
            }

            final Path packRoot = starterPacksRoot.resolve(manifest.sourceRef()).normalize();
            assertThat(packRoot.startsWith(starterPacksRoot)).isTrue();
            assertThat(Files.isDirectory(packRoot)).isTrue();
            assertThat(containsMaterializableFile(packRoot))
                    .withFailMessage("Active repo-pack manifest points to placeholder-only pack: %s", manifest.key())
                    .isTrue();
        }
    }

    private boolean containsMaterializableFile(final Path packRoot) throws IOException {
        try (var pathStream = Files.walk(packRoot)) {
            return pathStream
                    .filter(Files::isRegularFile)
                    .map(packRoot::relativize)
                    .map(Path::toString)
                    .anyMatch(path -> !".gitkeep".equals(path));
        }
    }
}
