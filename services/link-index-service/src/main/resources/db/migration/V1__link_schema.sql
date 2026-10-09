-- S07 — Link Index, state machine, dispositions (ADR-0016 platform-owned state).
--
-- Table prefix `link_` so this service can share one PostgreSQL instance with the
-- Polarion / Teamcenter adapters without colliding on table names.

CREATE TABLE link_artefact (
    native_id      TEXT PRIMARY KEY,
    uri            TEXT NOT NULL,
    version        TEXT NOT NULL DEFAULT '1',
    tool           TEXT NOT NULL,
    native_state   TEXT,
    deleted_at     TIMESTAMPTZ
);

CREATE INDEX link_artefact_uri_idx ON link_artefact (uri);

CREATE TABLE link_link (
    id                 TEXT PRIMARY KEY,
    relation           TEXT NOT NULL,
    source_native_id   TEXT NOT NULL,
    target_native_id   TEXT NOT NULL,
    source_uri         TEXT NOT NULL,
    target_uri         TEXT NOT NULL,
    -- FR-S13: both endpoint versions are pinned at the moment the link transitions
    -- from proposed to asserted (or is seeded already asserted).
    source_version     TEXT NOT NULL,
    target_version     TEXT NOT NULL,
    state              TEXT NOT NULL,
    cause              TEXT,
    actor              TEXT,
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT link_state_valid CHECK (state IN
        ('proposed', 'asserted', 'validated', 'superseded', 'dangling'))
);

CREATE INDEX link_source_idx ON link_link (source_native_id);
CREATE INDEX link_target_idx ON link_link (target_native_id);
CREATE INDEX link_source_uri_idx ON link_link (source_uri);
CREATE INDEX link_target_uri_idx ON link_link (target_uri);
CREATE INDEX link_state_idx ON link_link (state);

CREATE TABLE link_disposition (
    id                 UUID PRIMARY KEY,
    link_id            TEXT NOT NULL REFERENCES link_link(id),
    owner              TEXT NOT NULL,
    previous_version   TEXT NOT NULL,
    new_version        TEXT NOT NULL,
    cause_event_id     TEXT,
    opened_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at        TIMESTAMPTZ,
    outcome            TEXT,
    actor              TEXT,
    CONSTRAINT disposition_outcome_valid CHECK (
        outcome IS NULL OR outcome IN ('confirmed', 'updated', 'to-rerun', 'not-applicable'))
);

CREATE INDEX link_disposition_link_idx ON link_disposition (link_id);
CREATE INDEX link_disposition_owner_idx ON link_disposition (owner);
CREATE INDEX link_disposition_open_idx ON link_disposition (owner) WHERE resolved_at IS NULL;

CREATE TABLE link_event_outbox (
    id                 BIGSERIAL PRIMARY KEY,
    topic              TEXT NOT NULL,
    kafka_key          TEXT,
    event_id           UUID NOT NULL,
    event_type         TEXT NOT NULL,
    event_source       TEXT NOT NULL,
    event_subject      TEXT NOT NULL,
    cloud_event_json   BYTEA NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at       TIMESTAMPTZ
);

CREATE INDEX link_event_outbox_pending_idx ON link_event_outbox (id) WHERE published_at IS NULL;
