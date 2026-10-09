package org.eda.adapter.teamcenter.persistence;

import io.cloudevents.CloudEvent;
import io.cloudevents.core.provider.EventFormatProvider;
import io.cloudevents.jackson.JsonFormat;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class OutboxRepository {

    public record Row(
            long id,
            String topic,
            String kafkaKey,
            UUID eventId,
            String eventType,
            String eventSource,
            String eventSubject,
            byte[] cloudEventJson,
            OffsetDateTime createdAt,
            OffsetDateTime publishedAt) {
    }

    private final JdbcClient jdbc;

    public OutboxRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public void enqueue(String topic, String kafkaKey, CloudEvent event) {
        byte[] json = EventFormatProvider.getInstance()
                .resolveFormat(JsonFormat.CONTENT_TYPE)
                .serialize(event);

        jdbc.sql("""
                INSERT INTO tc_event_outbox
                    (topic, kafka_key, event_id, event_type,
                     event_source, event_subject, cloud_event_json)
                VALUES
                    (:topic, :key, :eventId, :type, :source, :subject, :json)
                """)
                .param("topic", topic)
                .param("key", kafkaKey)
                .param("eventId", UUID.fromString(event.getId()))
                .param("type", event.getType())
                .param("source", event.getSource().toString())
                .param("subject", event.getSubject())
                .param("json", json, Types.BINARY)
                .update();
    }

    public List<Row> loadPending(int limit) {
        return jdbc.sql("""
                SELECT id, topic, kafka_key, event_id, event_type, event_source,
                       event_subject, cloud_event_json, created_at, published_at
                  FROM tc_event_outbox
                 WHERE published_at IS NULL
              ORDER BY id
                 LIMIT :limit
                """)
                .param("limit", limit)
                .query((rs, rowNum) -> new Row(
                        rs.getLong("id"),
                        rs.getString("topic"),
                        rs.getString("kafka_key"),
                        (UUID) rs.getObject("event_id"),
                        rs.getString("event_type"),
                        rs.getString("event_source"),
                        rs.getString("event_subject"),
                        rs.getBytes("cloud_event_json"),
                        rs.getObject("created_at", OffsetDateTime.class),
                        rs.getObject("published_at", OffsetDateTime.class)))
                .list();
    }

    public void markPublished(long id) {
        jdbc.sql("UPDATE tc_event_outbox SET published_at = now() WHERE id = :id")
                .param("id", id)
                .update();
    }

    public long count() {
        return jdbc.sql("SELECT count(*) FROM tc_event_outbox").query(Long.class).single();
    }

    public long countPending() {
        return jdbc.sql("SELECT count(*) FROM tc_event_outbox WHERE published_at IS NULL")
                .query(Long.class)
                .single();
    }
}
