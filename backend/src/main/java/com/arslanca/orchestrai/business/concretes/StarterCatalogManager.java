package com.arslanca.orchestrai.business.concretes;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import com.arslanca.orchestrai.business.abstracts.StarterCatalogService;
import com.arslanca.orchestrai.business.dtos.projectgeneration.StarterManifest;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;

@Service
public class StarterCatalogManager implements StarterCatalogService {

    static final String DEFAULT_CATALOG_PATTERN = "classpath*:project-generation/catalog/*.json";
    private static final Logger log = LoggerFactory.getLogger(StarterCatalogManager.class);

    private final ObjectMapper objectMapper;
    private final String catalogPattern;
    private List<StarterManifest> approvedStarters = List.of();
    private Map<String, StarterManifest> startersByKey = Map.of();

    @Autowired
    public StarterCatalogManager(final ObjectMapper objectMapper) {
        this(objectMapper, DEFAULT_CATALOG_PATTERN);
    }

    StarterCatalogManager(final ObjectMapper objectMapper, final String catalogPattern) {
        this.objectMapper = objectMapper;
        this.catalogPattern = catalogPattern;
    }

    @PostConstruct
    public void loadCatalog() {
        final Resource[] resources;
        try {
            resources = new PathMatchingResourcePatternResolver().getResources(catalogPattern);
        } catch (final IOException exception) {
            throw new IllegalStateException("Failed to resolve starter catalog resources", exception);
        }

        if (resources.length == 0) {
            throw new IllegalStateException("No starter catalog resources found for pattern: " + catalogPattern);
        }

        Arrays.sort(resources, Comparator.comparing(Resource::getFilename, Comparator.nullsLast(String::compareTo)));

        final Map<String, StarterManifest> loadedStarters = new LinkedHashMap<>();
        for (final Resource resource : resources) {
            final StarterManifest manifest = readManifest(resource);
            final StarterManifest previousManifest = loadedStarters.putIfAbsent(manifest.key(), manifest);
            if (previousManifest != null) {
                throw new IllegalStateException("Duplicate starter manifest key detected: " + manifest.key());
            }
        }

        this.approvedStarters = loadedStarters.values().stream()
                .filter(StarterManifest::isSelectable)
                .toList();
        this.startersByKey = Map.copyOf(loadedStarters);

        final long disabledCount = loadedStarters.values().stream()
                .filter(manifest -> !manifest.isSelectable())
                .count();
        log.info("Loaded {} selectable starter manifests ({} disabled) from {}",
                approvedStarters.size(), disabledCount, catalogPattern);
    }

    @Override
    public List<StarterManifest> getApprovedStarters() {
        return approvedStarters;
    }

    @Override
    public Optional<StarterManifest> findByKey(final String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(startersByKey.get(key.trim().toLowerCase()));
    }

    private StarterManifest readManifest(final Resource resource) {
        try (InputStream inputStream = resource.getInputStream()) {
            return objectMapper.readValue(inputStream, StarterManifest.class);
        } catch (final IOException exception) {
            if (exception.getCause() instanceof IllegalArgumentException) {
                throw new IllegalStateException("Invalid starter manifest: " + resource.getFilename(), exception.getCause());
            }
            throw new IllegalStateException("Failed to parse starter manifest: " + resource.getFilename(), exception);
        } catch (final IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid starter manifest: " + resource.getFilename(), exception);
        }
    }
}
