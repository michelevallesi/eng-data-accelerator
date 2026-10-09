-- Resolver schema (S02, ADR-0016, ADR-0018).
-- URIs are opaque UUIDs under a tool namespace (AD-03). Platform-owned state is
-- written transactionally with its outbox event.

CREATE TABLE uri (
    uri          TEXT PRIMARY KEY,
    tool         TEXT NOT NULL,
    native_id    TEXT NOT NULL,
    deep_link    TEXT NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(tool, native_id)
);

CREATE TABLE uri_alias (
    from_uri    TEXT PRIMARY KEY REFERENCES uri(uri),
    to_uri      TEXT NOT NULL REFERENCES uri(uri),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK (from_uri <> to_uri)
);

CREATE TABLE uri_tombstone (
    uri             TEXT PRIMARY KEY REFERENCES uri(uri),
    reason          TEXT,
    tombstoned_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE outbox (
    id              BIGSERIAL PRIMARY KEY,
    event_id        UUID        NOT NULL UNIQUE,
    event_type      TEXT        NOT NULL,
    event_source    TEXT        NOT NULL,
    event_subject   TEXT        NOT NULL,
    event_time      TIMESTAMPTZ NOT NULL,
    data_json       JSONB       NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at    TIMESTAMPTZ
);

CREATE INDEX outbox_unpublished_idx ON outbox (id) WHERE published_at IS NULL;
CREATE INDEX outbox_type_idx ON outbox (event_type);
