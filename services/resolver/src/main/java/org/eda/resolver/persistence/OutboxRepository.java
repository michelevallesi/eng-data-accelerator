package org.eda.resolver.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    private final JdbcClient jdbc;
    private final ObjectMapper mapper;

    public OutboxRepository(JdbcClient jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    public void insert(OutboxEvent event) {
        try {
            String json = mapper.writeValueAsString(event.data());
            jdbc.sql("""
                    INSERT INTO outbox
                        (event_id, event_type, event_source, event_subject, event_time, data_json)
                    VALUES
                        (:eventId, :type, :source, :subject, :time, :data::jsonb)
                    """)
                    .param("eventId", event.eventId())
                    .param("type", event.type())
                    .param("source", event.source())
                    .param("subject", event.subject())
                    .param("time", event.time())
                    .param("data", json, Types.OTHER)
                    .update();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("failed to serialise outbox data", e);
        }
    }

    public List<OutboxRow> all() {
        return jdbc.sql("""
                SELECT id, event_id, event_type, event_source, event_subject,
                       event_time, data_json, created_at, published_at
                  FROM outbox
              ORDER BY id
                """)
                .query((rs, rowNum) -> new OutboxRow(
                        rs.getLong("id"),
                        (UUID) rs.getObject("event_id"),
                        rs.getString("event_type"),
                        rs.getString("event_source"),
                        rs.getString("event_subject"),
                        rs.getObject("event_time", OffsetDateTime.class),
                        rs.getString("data_json"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("published_at", OffsetDateTime.class)))
                .list();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM outbox")
                .query(Long.class)
                .single();
    }

    public long countByType(String type) {
        return jdbc.sql("SELECT count(*) FROM outbox WHERE event_type = :type")
                .param("type", type)
                .query(Long.class)
                .single();
    }

    public record OutboxEvent(
            UUID eventId,
            String type,
            String source,
            String subject,
            OffsetDateTime time,
            Map<String, Object> data) {
    }

    public record OutboxRow(
            long id,
            UUID eventId,
            String eventType,
            String eventSource,
            String eventSubject,
            OffsetDateTime eventTime,
            String dataJson,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt) {
    }
}
