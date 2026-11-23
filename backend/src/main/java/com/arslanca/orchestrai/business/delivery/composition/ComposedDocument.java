package com.arslanca.orchestrai.business.delivery.composition;

import java.util.List;
import java.util.Objects;

public record ComposedDocument(
        DocumentKind kind,
        String id,
        String title,
        List<String> sections,
        String content) {

    public ComposedDocument {
        kind = Objects.requireNonNull(kind, "kind must not be null");
        id = id == null || id.isBlank() ? "untitled" : id.trim();
        title = title == null ? "" : title.trim();
        sections = sections == null ? List.of() : List.copyOf(sections);
        content = content == null ? "" : content;
    }
}
