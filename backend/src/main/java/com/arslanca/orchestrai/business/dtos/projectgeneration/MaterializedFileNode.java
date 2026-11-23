package com.arslanca.orchestrai.business.dtos.projectgeneration;

public record MaterializedFileNode(
        String path,
        String content,
        String contentEncoding,
        long sizeBytes,
        boolean binary) {

    public MaterializedFileNode {
        path = normalize(path);
        content = content == null ? "" : content;
        contentEncoding = normalizeEncoding(contentEncoding);
        if (sizeBytes < 0L) {
            throw new IllegalArgumentException("sizeBytes cannot be negative");
        }
    }

    private static String normalize(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("path cannot be blank");
        }
        return value.trim();
    }

    private static String normalizeEncoding(final String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("contentEncoding cannot be blank");
        }
        return value.trim().toLowerCase();
    }
}
