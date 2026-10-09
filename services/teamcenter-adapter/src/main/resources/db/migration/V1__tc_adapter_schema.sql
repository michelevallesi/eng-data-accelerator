-- Teamcenter adapter state (S05, ADR-0016). Table names are prefixed `tc_` so a
-- shared production Postgres can host the Polarion and Teamcenter adapters side
-- by side without collision.

CREATE TABLE tc_trs_cursor (
    source      TEXT PRIMARY KEY,
    last_order  BIGINT NOT NULL,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE tc_processed_events (
    source           TEXT   NOT NULL,
    trs_order        BIGINT NOT NULL,
    status           TEXT   NOT NULL,
    cloud_event_id   UUID   NOT NULL,
    processed_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (source, trs_order)
);

CREATE INDEX tc_processed_events_status_idx ON tc_processed_events (status);
