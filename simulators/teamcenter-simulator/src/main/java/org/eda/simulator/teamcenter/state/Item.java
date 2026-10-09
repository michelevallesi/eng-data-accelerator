package org.eda.simulator.teamcenter.state;

public record Item(
        String nativeId,
        String uri,
        String description,
        String nativeState,
        int version) {

    public Item withNewVersion(String newDescription) {
        return new Item(nativeId, uri, newDescription, nativeState, version + 1);
    }
}
