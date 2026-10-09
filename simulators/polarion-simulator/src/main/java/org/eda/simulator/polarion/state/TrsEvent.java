package org.eda.simulator.polarion.state;

import java.time.OffsetDateTime;

public record TrsEvent(
        long order,
        Kind kind,
        String resourceUri,
        int version,
        OffsetDateTime occurredAt) {

    public enum Kind { CREATION, MODIFICATION, DELETION }
}
