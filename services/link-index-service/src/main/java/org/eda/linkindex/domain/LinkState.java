package org.eda.linkindex.domain;

/**
 * The only link states permitted on the platform. Order mirrors the lifecycle
 * from PRD chapter 12 / ADR-0006 / ADR-0010. "Verified" is explicitly NOT in
 * the vocabulary — CLAUDE.md non-negotiable.
 */
public enum LinkState {
    PROPOSED,
    ASSERTED,
    VALIDATED,
    SUPERSEDED,
    DANGLING;

    public String wire() {
        return name().toLowerCase();
    }

    public static LinkState fromWire(String wire) {
        return LinkState.valueOf(wire.toUpperCase().replace('-', '_'));
    }
}
