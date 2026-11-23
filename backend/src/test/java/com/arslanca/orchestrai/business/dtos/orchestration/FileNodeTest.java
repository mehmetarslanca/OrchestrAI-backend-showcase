package com.arslanca.orchestrai.business.dtos.orchestration;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import static org.assertj.core.api.Assertions.assertThat;

class FileNodeTest {

    @Test
    void shouldNormalizeTextFileProperly() {
        FileNode node = new FileNode("src/main.java", "public class Main {}");
        
        assertThat(node.path()).isEqualTo("src/main.java");
        assertThat(node.content()).isEqualTo("public class Main {}");
        assertThat(node.contentEncoding()).isEqualTo(FileNode.UTF_8);
        assertThat(node.sizeBytes()).isEqualTo("public class Main {}".getBytes(StandardCharsets.UTF_8).length);
        assertThat(node.binary()).isFalse();
    }

    @Test
    void shouldHandleBinaryFileWithBase64Encoding() {
        String base64Content = Base64.getEncoder().encodeToString("binary_data".getBytes(StandardCharsets.UTF_8));
        FileNode node = new FileNode("image.png", base64Content, FileNode.BASE64, 0, true);
        
        assertThat(node.path()).isEqualTo("image.png");
        assertThat(node.content()).isEqualTo(base64Content);
        assertThat(node.contentEncoding()).isEqualTo(FileNode.BASE64);
        assertThat(node.binary()).isTrue();
        assertThat(node.sizeBytes()).isEqualTo("binary_data".getBytes(StandardCharsets.UTF_8).length);
    }
    
    @Test
    void shouldFallbackToZeroSizeOnInvalidBase64() {
        FileNode node = new FileNode("image.png", "invalid_base64!!!", FileNode.BASE64, 0, true);
        assertThat(node.sizeBytes()).isZero();
    }

    @Test
    void shouldMaintainProvidedSizeIfGreaterThanZero() {
        FileNode node = new FileNode("big.txt", "content", FileNode.UTF_8, 5000L, false);
        assertThat(node.sizeBytes()).isEqualTo(5000L);
    }
}
