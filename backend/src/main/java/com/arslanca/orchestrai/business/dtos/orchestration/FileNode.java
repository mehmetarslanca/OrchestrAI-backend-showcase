package com.arslanca.orchestrai.business.dtos.orchestration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Represents a single file in the final project artifact tree.
 *
 * <p>
 * The CLI receives a list of these and writes each
 * to the user's local file system.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileNode(
        String path,
        String content,
        String contentEncoding,
        long sizeBytes,
        boolean binary) {

    public static final String UTF_8 = "utf-8";
    public static final String BASE64 = "base64";

    public FileNode {
        path = path == null ? null : path.trim();
        content = content == null ? "" : content;
        contentEncoding = normalizeEncoding(contentEncoding, binary);
        sizeBytes = normalizeSize(content, contentEncoding, sizeBytes, binary);
    }

    public FileNode(String path, String content) {
        this(path, content, UTF_8, content != null ? content.getBytes(StandardCharsets.UTF_8).length : 0L, false);
    }

    private static String normalizeEncoding(final String value, final boolean binary) {
        if (value == null || value.isBlank()) {
            return binary ? BASE64 : UTF_8;
        }
        return value.trim().toLowerCase();
    }

    private static long normalizeSize(
            final String content,
            final String contentEncoding,
            final long sizeBytes,
            final boolean binary) {
        if (sizeBytes > 0L) {
            return sizeBytes;
        }
        if (content == null || content.isEmpty()) {
            return 0L;
        }
        if (!binary) {
            return content.getBytes(StandardCharsets.UTF_8).length;
        }
        if (BASE64.equals(contentEncoding)) {
            try {
                return Base64.getDecoder().decode(content).length;
            } catch (IllegalArgumentException ignored) {
                return 0L;
            }
        }
        return 0L;
    }
}
