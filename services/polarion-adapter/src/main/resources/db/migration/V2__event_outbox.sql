-- Risk #1: full ADR-0016 compliance. Every state change (processed_events row) is
-- written in the same transaction as its outbox row; a separate relay publishes the
-- outbox rows to Kafka at-least-once with deterministic CloudEvent ids so downstream
-- dedup remains trivial.

CREATE TABLE event_outbox (
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

CREATE INDEX event_outbox_pending_idx ON event_outbox (id) WHERE published_at IS NULL;
CREATE INDEX event_outbox_type_idx ON event_outbox (event_type);
