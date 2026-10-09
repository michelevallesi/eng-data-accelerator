package org.eda.resolver.events;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.eda.resolver.persistence.OutboxRepository.OutboxEvent;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Builds the {@code eda.uri.*} CloudEvents emitted by the Resolver (ADR-0026).
 * <p>Attributes come from ADR-0026: {@code source}, {@code type}, {@code subject},
 * {@code id}, {@code time}, plus a JSON {@code data} payload carrying the context.
 */
@Component
public class UriCloudEvents {

    public static final String TYPE_MINTED = "eda.uri.minted";
    public static final String TYPE_ALIASED = "eda.uri.aliased";
    public static final String TYPE_TOMBSTONED = "eda.uri.tombstoned";

    private final String source;

    public UriCloudEvents(@Value("${eda.event-source}") String source) {
        this.source = source;
    }

    public OutboxEvent minted(String uri, String tool, String nativeId, OffsetDateTime time) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uri", uri);
        data.put("tool", tool);
        data.put("nativeId", nativeId);
        return new OutboxEvent(UUID.randomUUID(), TYPE_MINTED, source, uri, time, data);
    }

    public OutboxEvent aliased(String fromUri, String toUri, OffsetDateTime time) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fromUri", fromUri);
        data.put("toUri", toUri);
        return new OutboxEvent(UUID.randomUUID(), TYPE_ALIASED, source, fromUri, time, data);
    }

    public OutboxEvent tombstoned(String uri, String reason, OffsetDateTime time) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("uri", uri);
        data.put("reason", reason);
        return new OutboxEvent(UUID.randomUUID(), TYPE_TOMBSTONED, source, uri, time, data);
    }
}
