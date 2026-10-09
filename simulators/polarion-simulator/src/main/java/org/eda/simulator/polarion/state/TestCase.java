package org.eda.simulator.polarion.state;

public record TestCase(
        String nativeId,
        String uri,
        String description,
        String nativeState,
        int version) {

    public TestCase withDescription(String newDescription) {
        return new TestCase(nativeId, uri, newDescription, nativeState, version + 1);
    }
}
