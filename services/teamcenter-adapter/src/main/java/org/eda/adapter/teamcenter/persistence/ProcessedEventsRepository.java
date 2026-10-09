package org.eda.adapter.teamcenter.persistence;

import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class ProcessedEventsRepository {

    public enum Status { PUBLISHED, DLQ }

    private final JdbcClient jdbc;

    public ProcessedEventsRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public boolean isProcessed(String source, long trsOrder) {
        Long count = jdbc.sql("""
                SELECT count(*) FROM tc_processed_events
                 WHERE source = :source AND trs_order = :order
                """)
                .param("source", source)
                .param("order", trsOrder)
                .query(Long.class)
                .single();
        return count > 0;
    }

    public void record(String source, long trsOrder, Status status, UUID cloudEventId) {
        jdbc.sql("""
                INSERT INTO tc_processed_events (source, trs_order, status, cloud_event_id)
                VALUES (:source, :order, :status, :eventId)
                ON CONFLICT (source, trs_order) DO NOTHING
                """)
                .param("source", source)
                .param("order", trsOrder)
                .param("status", status.name())
                .param("eventId", cloudEventId)
                .update();
    }

    public void forget(String source, long trsOrder) {
        jdbc.sql("DELETE FROM tc_processed_events WHERE source = :source AND trs_order = :order")
                .param("source", source)
                .param("order", trsOrder)
                .update();
    }
}
