package org.eda.linkindex.domain;

import java.time.OffsetDateTime;

/**
 * One row of {@code link_link}. Immutable; state transitions produce a new
 * record that overwrites the existing row in the same tx as the outbox write.
 *
 * @param id              fixture-provided URI, e.g. {@code https://eda.example.org/link/<uuid>}
 * @param relation        link relation name (fixture convention, e.g. {@code satisfiedBy})
 * @param sourceNativeId  source artefact native id (e.g. {@code REQ-019})
 * @param targetNativeId  target artefact native id
 * @param sourceUri       source opaque URI (resolver-minted)
 * @param targetUri       target opaque URI
 * @param sourceVersion   source version pinned at assertion time (FR-S13)
 * @param targetVersion   target version pinned at assertion time (FR-S13)
 * @param state           {@link LinkState} on the lifecycle
 * @param cause           free-form provenance string (e.g. {@code fixture-seed},
 *                        {@code resource-changed:<ce_id>}, {@code disposition-confirmed:<id>})
 * @param actor           user id for human-driven transitions (null otherwise)
 * @param updatedAt       last transition timestamp
 */
public record LinkRecord(
        String id,
        String relation,
        String sourceNativeId,
        String targetNativeId,
        String sourceUri,
        String targetUri,
        String sourceVersion,
        String targetVersion,
        LinkState state,
        String cause,
        String actor,
        OffsetDateTime updatedAt) {
}
