package org.eda.linkindex.domain;

/**
 * Outcomes an owner can record on a disposition (FR-S14, FR-S15). "Confirmed"
 * re-asserts the link on the new version; the others close the disposition
 * without re-assertion.
 */
public enum DispositionOutcome {
    CONFIRMED("confirmed"),
    UPDATED("updated"),
    TO_RERUN("to-rerun"),
    NOT_APPLICABLE("not-applicable");

    private final String wire;

    DispositionOutcome(String wire) {
        this.wire = wire;
    }

    public String wire() {
        return wire;
    }

    public static DispositionOutcome fromWire(String wire) {
        for (DispositionOutcome o : values()) {
            if (o.wire.equals(wire)) {
                return o;
            }
        }
        throw new IllegalArgumentException("unknown disposition outcome: " + wire);
    }
}
