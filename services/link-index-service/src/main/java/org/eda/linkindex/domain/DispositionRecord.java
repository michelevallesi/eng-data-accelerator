package org.eda.linkindex.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row of {@code link_disposition}. Platform-owned state (ADR-0016); every
 * open / resolve is paired with an {@code eda.link.state-changed} event.
 */
public record DispositionRecord(
        UUID id,
        String linkId,
        String owner,
        String previousVersion,
        String newVersion,
        String causeEventId,
        OffsetDateTime openedAt,
        OffsetDateTime resolvedAt,
        DispositionOutcome outcome,
        String actor) {

    public boolean isOpen() {
        return resolvedAt == null;
    }
}
