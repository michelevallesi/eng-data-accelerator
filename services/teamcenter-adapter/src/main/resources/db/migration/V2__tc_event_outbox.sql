-- ADR-0016 outbox for the Teamcenter adapter.

CREATE TABLE tc_event_outbox (
    id                BIGSERIAL PRIMARY KEY,
    topic             TEXT        NOT NULL,
    kafka_key         TEXT,
    event_id          UUID        NOT NULL,
    event_type        TEXT        NOT NULL,
    event_source      TEXT        NOT NULL,
    event_subject     TEXT        NOT NULL,
    cloud_event_json  BYTEA       NOT NULL,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at      TIMESTAMPTZ
);

CREATE INDEX tc_event_outbox_pending_idx ON tc_event_outbox (id) WHERE published_at IS NULL;
CREATE INDEX tc_event_outbox_type_idx ON tc_event_outbox (event_type);
