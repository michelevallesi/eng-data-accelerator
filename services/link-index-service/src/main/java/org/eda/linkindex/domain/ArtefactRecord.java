package org.eda.linkindex.domain;

import java.time.OffsetDateTime;

/**
 * One row of {@code link_artefact}. The link index knows just enough about an
 * artefact to pin link endpoints: its URI, its current version, which tool it
 * lives in, and whether it's been tombstoned.
 */
public record ArtefactRecord(
        String nativeId,
        String uri,
        String version,
        String tool,
        String nativeState,
        OffsetDateTime deletedAt) {

    public boolean isDeleted() {
        return deletedAt != null;
    }
}
