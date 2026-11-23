package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.abstracts.delivery.BlueprintDeliveryService;
import com.arslanca.orchestrai.business.dtos.delivery.DeliveryResult;
import com.arslanca.orchestrai.business.dtos.projectgeneration.BlueprintPlan;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedFileNode;
import com.arslanca.orchestrai.business.dtos.projectgeneration.MaterializedStarter;
import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.core.config.projectgeneration.ArtifactSecurityProperties;
import com.arslanca.orchestrai.entity.enums.IdeTarget;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ArtifactAssemblyManagerTest {

    @Mock
    private BlueprintDeliveryService blueprintDeliveryService;

    private ArtifactAssemblyManager manager;

    private BlueprintPlan blueprint;
    private MaterializedStarter starter;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        manager = new ArtifactAssemblyManager(
                blueprintDeliveryService,
                new ArtifactSecurityValidator(new ArtifactSecurityProperties(), new SimpleMeterRegistry()),
                new com.arslanca.orchestrai.business.delivery.policy.DefaultReservedPathPolicy());
        blueprint = new BlueprintPlan("test-project", "desc", List.of(), List.of(), "rules", "notes");
        when(blueprintDeliveryService.deliver(any(), any()))
                .thenReturn(new DeliveryResult("test-project", "cursor", List.of(), null));
    }

    private MaterializedStarter createStarter(String path, String content) {
        return new MaterializedStarter("key", com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind.REPO_PACK, "ref", List.of(new MaterializedFileNode(path, content, "utf-8", content == null ? 0L : content.length(), false)), null, false);
    }

    private MaterializedStarter createStarter(String path, String content, boolean binary) {
        final String normalizedContent = binary
                ? Base64.getEncoder().encodeToString((content == null ? "" : content).getBytes(StandardCharsets.UTF_8))
                : content;
        final long sizeBytes = content == null ? 0L : content.getBytes(StandardCharsets.UTF_8).length;
        return new MaterializedStarter("key", com.arslanca.orchestrai.business.dtos.projectgeneration.StarterSourceKind.REPO_PACK, "ref", List.of(new MaterializedFileNode(path, normalizedContent, binary ? "base64" : "utf-8", sizeBytes, binary)), null, false);
    }

    @Test
    void shouldRejectAbsolutePaths() {
        starter = createStarter("/absolute/path.txt", "content");
        assertThrows(IllegalArgumentException.class, () -> manager.assembleArtifact(blueprint, starter, IdeTarget.CURSOR));
    }

    @Test
    void shouldRejectWindowsDriveAbsolutePaths() {
        starter = createStarter("C:\\windows\\path.txt", "content");
        assertThrows(IllegalArgumentException.class, () -> manager.assembleArtifact(blueprint, starter, IdeTarget.CURSOR));
    }

    @Test
    void shouldRejectDirectoryTraversal() {
        starter = createStarter("some/../path.txt", "content");
        assertThrows(IllegalArgumentException.class, () -> manager.assembleArtifact(blueprint, starter, IdeTarget.CURSOR));
    }

    @Test
    void shouldRejectReservedPathCollision() {
        starter = createStarter(".cursorrules", "content");
        assertThrows(IllegalStateException.class, () -> manager.assembleArtifact(blueprint, starter, IdeTarget.CURSOR));
    }

    @Test
    void shouldRejectOversizeArtifact() {
        String oversize = "a".repeat(25_000_001);
        starter = createStarter("large.txt", oversize);
        assertThrows(IllegalStateException.class, () -> manager.assembleArtifact(blueprint, starter, IdeTarget.CURSOR));
    }

    @Test
    void shouldPreserveTrustedBinaryStarterFiles() {
        starter = createStarter(".mvn/wrapper/maven-wrapper.jar", "wrapper-binary", true);
        DeliveryResult result = manager.assembleArtifact(blueprint, starter, IdeTarget.CURSOR);
        assertFalse(result.tree().isEmpty(), "Binary file should be preserved in artifact");
        assertTrue(result.tree().get(0).binary(), "File node should be marked as binary");
        assertEquals("base64", result.tree().get(0).contentEncoding());
        assertEquals(".mvn/wrapper/maven-wrapper.jar", result.tree().get(0).path());
    }

    @Test
    void shouldValidateSystemFilesToo() {
        when(blueprintDeliveryService.deliver(any(), any()))
                .thenReturn(new DeliveryResult("test-project", "cursor", List.of(new FileNode("/system/bad.txt", "c")), null));
        assertThrows(IllegalArgumentException.class, () -> manager.assembleArtifact(blueprint, null, IdeTarget.CURSOR));
    }

    @Test
    void shouldHandleNullContentSafely() {
        when(blueprintDeliveryService.deliver(any(), any()))
                .thenReturn(new DeliveryResult("test-project", "cursor", List.of(new FileNode("nullcontent.txt", null)), null));
        DeliveryResult result = manager.assembleArtifact(blueprint, null, IdeTarget.CURSOR);
        assertEquals(1, result.tree().size());
        assertEquals("", result.tree().get(0).content());
    }

    @Test
    void shouldRejectReservedPathCollision_trickyPaths() {
        // bypass attempt using `./`
        assertThrows(IllegalStateException.class, () -> 
            manager.assembleArtifact(blueprint, createStarter("./.cursorrules", "content"), IdeTarget.CURSOR));
            
        // bypass attempt using `//`
        assertThrows(IllegalStateException.class, () -> 
            manager.assembleArtifact(blueprint, createStarter("docs//architect.md", "content"), IdeTarget.CURSOR));
            
        // bypass attempt using trailing space
        assertThrows(IllegalStateException.class, () -> 
            manager.assembleArtifact(blueprint, createStarter("docs/architect.md ", "content"), IdeTarget.CURSOR));

        // bypass attempt using case
        assertThrows(IllegalStateException.class, () -> 
            manager.assembleArtifact(blueprint, createStarter(".CursorRules", "content"), IdeTarget.CURSOR));
            
        assertThrows(IllegalStateException.class, () -> 
            manager.assembleArtifact(blueprint, createStarter("DOCS/architect.md", "content"), IdeTarget.CURSOR));

        // bypass attempt using internal dot segments
        assertThrows(IllegalArgumentException.class, () -> 
            manager.assembleArtifact(blueprint, createStarter("docs/./architect.md", "content"), IdeTarget.CURSOR));
    }

    @Test
    void shouldRejectBlankPathsAfterNormalization() {
        when(blueprintDeliveryService.deliver(any(), any()))
                .thenReturn(new DeliveryResult("test-project", "cursor", List.of(new FileNode("   ", "content")), null));
        assertThrows(IllegalArgumentException.class, () -> manager.assembleArtifact(blueprint, null, IdeTarget.CURSOR));
    }
}
