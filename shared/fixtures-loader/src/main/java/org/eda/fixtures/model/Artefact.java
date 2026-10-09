package org.eda.fixtures.model;

public record Artefact(
        String nativeId,
        String tool,
        String type,
        String description,
        String nativeState,
        Integer trl,
        String uri) {
}
