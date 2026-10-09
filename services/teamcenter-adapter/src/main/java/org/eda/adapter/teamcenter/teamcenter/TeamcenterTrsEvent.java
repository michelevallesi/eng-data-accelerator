package org.eda.adapter.teamcenter.teamcenter;

public record TeamcenterTrsEvent(long order, Kind kind, String resourceUri) {

    public enum Kind { CREATION, MODIFICATION, DELETION }

    public boolean isDeletion() {
        return kind == Kind.DELETION;
    }
}
