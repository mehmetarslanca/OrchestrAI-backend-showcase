package com.arslanca.orchestrai.business.concretes;

import com.arslanca.orchestrai.business.dtos.orchestration.FileNode;
import com.arslanca.orchestrai.core.config.projectgeneration.ArtifactSecurityProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ArtifactSecurityValidatorTest {

    private ArtifactSecurityValidator validator;
    private ArtifactSecurityProperties properties;
    private SimpleMeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        properties = new ArtifactSecurityProperties();
        meterRegistry = new SimpleMeterRegistry();
        validator = new ArtifactSecurityValidator(properties, meterRegistry);
    }

    @Test
    void shouldAcceptValidFiles() {
        List<FileNode> files = List.of(
            new FileNode("src/main/java/Main.java", "public class Main {}"),
            new FileNode("README.md", "# Hello"),
            new FileNode(".env.example", "API_KEY=YOUR_KEY")
        );
        
        assertDoesNotThrow(() -> validator.validateFiles(files));
        assertEquals(1, meterRegistry.summary("project_generation.artifact.file_count").count());
        assertEquals(3.0, meterRegistry.summary("project_generation.artifact.file_count").totalAmount());
    }

    @Test
    void shouldRejectForbiddenExtension() {
        List<FileNode> files = List.of(
            new FileNode("malicious.exe", "MZ")
        );
        
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Forbidden binary extension detected"));
        assertEquals(1.0, meterRegistry.counter("project_generation.security.rejection", "reason", "forbidden_extension").count());
    }

    @Test
    void shouldRejectSecretFiles() {
        List<FileNode> files = List.of(
            new FileNode(".env", "DB_PASS=secret")
        );
        
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Secret or credential file detected"));
        assertEquals(1.0, meterRegistry.counter("project_generation.security.rejection", "reason", "secret_file").count());
    }

    @Test
    void shouldRejectSecretContent() {
        List<FileNode> files = List.of(
            new FileNode("config.txt", "api_key=real_secret_value")
        );
        
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Secret content heuristics matched"));
        assertEquals(1.0, meterRegistry.counter("project_generation.security.rejection", "reason", "secret_content").count());
    }

    @Test
    void shouldRejectMaxTotalBytes() {
        properties.setMaxTotalBytes(10);
        List<FileNode> files = List.of(
            new FileNode("test.txt", "12345678901") // 11 bytes
        );
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Final artifact size exceeds maximum allowed limit"));
    }

    @Test
    void shouldRejectMaxSingleFileBytes() {
        properties.setMaxSingleFileBytes(5);
        List<FileNode> files = List.of(
            new FileNode("test.txt", "123456")
        );
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Single file size exceeds maximum allowed limit"));
    }

    @Test
    void shouldRejectMaxFileCount() {
        properties.setMaxFileCount(1);
        List<FileNode> files = List.of(
            new FileNode("a.txt", "a"),
            new FileNode("b.txt", "b")
        );
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Artifact file count exceeds maximum allowed limit"));
    }

    @Test
    void shouldRejectExtensionBypassWithTemplateSuffix() {
        List<FileNode> files = List.of(
            new FileNode("malware.env.example.exe", "MZ")
        );
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Forbidden binary extension detected"));
    }

    @Test
    void shouldRejectEnvSuffixBypass() {
        List<FileNode> files = List.of(
            new FileNode("config.env", "NORMAL_VAR=value")
        );
        
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Secret or credential file detected"));
        assertEquals(1.0, meterRegistry.counter("project_generation.security.rejection", "reason", "secret_file").count());
    }

    @Test
    void shouldRejectTemplateSecretBypass() {
        List<FileNode> files = List.of(
            new FileNode("config.example", "api_key=real_secret_value_sk_12345\nYOUR_VAR=dummy")
        );
        
        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Secret content heuristics matched"));
        assertEquals(1.0, meterRegistry.counter("project_generation.security.rejection", "reason", "secret_content").count());
    }

    @Test
    void shouldAllowTrustedBinaryFilesWithBase64Encoding() {
        String encoded = Base64.getEncoder().encodeToString("jar-bytes".getBytes(StandardCharsets.UTF_8));
        List<FileNode> files = List.of(
            new FileNode(".mvn/wrapper/maven-wrapper.jar", encoded, "base64", 9L, true)
        );

        assertDoesNotThrow(() -> validator.validateFiles(files));
    }

    @Test
    void shouldRejectBinaryFilesWithNonBase64Encoding() {
        List<FileNode> files = List.of(
            new FileNode("assets/logo.png", "not-base64", "utf-8", 10L, true)
        );

        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Binary files must use base64 contentEncoding"));
    }

    @Test
    void shouldRejectTraversalPaths() {
        List<FileNode> files = List.of(
            new FileNode("../../etc/passwd", "root:x:0:0")
        );

        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("traversal"));
        assertEquals(1.0, meterRegistry.counter("project_generation.security.rejection", "reason", "path_traversal").count());
    }

    @Test
    void shouldRejectAbsolutePaths() {
        List<FileNode> files = List.of(
            new FileNode("/var/tmp/file.txt", "hello")
        );

        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("Absolute artifact paths"));
        assertEquals(1.0, meterRegistry.counter("project_generation.security.rejection", "reason", "absolute_path").count());
    }

    @Test
    void shouldRejectTextLikeFilesMarkedAsBinary() {
        String encoded = Base64.getEncoder().encodeToString("real_secret_value".getBytes(StandardCharsets.UTF_8));
        List<FileNode> files = List.of(
            new FileNode("config/application.yaml", encoded, "base64", "real_secret_value".length(), true)
        );

        var ex = assertThrows(IllegalStateException.class, () -> validator.validateFiles(files));
        assertTrue(ex.getMessage().contains("cannot be marked binary"));
        assertEquals(1.0, meterRegistry.counter("project_generation.security.rejection", "reason", "unexpected_binary_text_file").count());
    }
}
