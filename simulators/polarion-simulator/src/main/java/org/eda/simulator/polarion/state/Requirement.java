package org.eda.simulator.polarion.state;

public record Requirement(
        String nativeId,
        String uri,
        String description,
        String nativeState,
        int version) {

    public Requirement withDescription(String newDescription) {
        return new Requirement(nativeId, uri, newDescription, nativeState, version + 1);
    }
}
