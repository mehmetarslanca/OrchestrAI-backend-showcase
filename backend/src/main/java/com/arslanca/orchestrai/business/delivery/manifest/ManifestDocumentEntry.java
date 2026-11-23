package com.arslanca.orchestrai.business.delivery.manifest;

import com.arslanca.orchestrai.business.delivery.composition.DocumentKind;

import java.util.List;

public record ManifestDocumentEntry(
        DocumentKind kind,
        String id,
        String title,
        List<String> sections,
        long contentLength,
        String renderedPath) {

    public ManifestDocumentEntry {
        id = id == null || id.isBlank() ? "untitled" : id.trim();
        title = title == null ? "" : title.trim();
        sections = sections == null ? List.of() : List.copyOf(sections);
        renderedPath = renderedPath == null ? "" : renderedPath.trim();
    }

    public ManifestDocumentEntry withRenderedPath(final String updatedRenderedPath) {
        return new ManifestDocumentEntry(kind, id, title, sections, contentLength, updatedRenderedPath);
    }
}
