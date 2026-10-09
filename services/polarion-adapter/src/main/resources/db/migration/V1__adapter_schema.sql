-- Polarion adapter state (S04, ADR-0016).
-- Cursor advances only after every event in a batch has been processed; a crash
-- between Kafka publish and processed_events insert is recovered by replay plus the
-- deterministic CloudEvent id (downstream dedup) and the local processed_events check.

CREATE TABLE trs_cursor (
    source      TEXT PRIMARY KEY,
    last_order  BIGINT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE processed_events (
    source           TEXT   NOT NULL,
    trs_order        BIGINT NOT NULL,
    status           TEXT   NOT NULL,        -- PUBLISHED | DLQ
    cloud_event_id   UUID   NOT NULL,
    processed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (source, trs_order)
);

CREATE INDEX processed_events_status_idx ON processed_events (status);
