package org.eda.adapter.polarion.polarion;

public record PolarionTrsEvent(long order, Kind kind, String resourceUri) {

    public enum Kind { CREATION, MODIFICATION, DELETION }

    public boolean isDeletion() {
        return kind == Kind.DELETION;
    }
}
