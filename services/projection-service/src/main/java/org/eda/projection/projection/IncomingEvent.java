package org.eda.projection.projection;

import io.cloudevents.CloudEvent;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;

/**
 * Projection-side view of a change event. Normalises the two Kafka wire cases:
 * (a) a parseable {@link CloudEvent} with a JSON-LD body (CREATION / MODIFICATION), and
 * (b) a Kafka tombstone where {@code value()} is null and attributes live only in headers
 *     (DELETION, per {@code docs/specs/asyncapi.yaml}).
 */
public record IncomingEvent(
        String id,
        String source,
        String subject,
        String kind,
        OffsetDateTime time,
        String sourceTime,
        String edaversion,
        Long trsOrder,
        byte[] jsonLdPayload) {

    public boolean isDeletion() {
        return "DELETION".equalsIgnoreCase(kind);
    }

    /**
     * FR-M08: the "data age" of a projected node is the time the source tool
     * last saw the resource ({@code edasourcetime}), falling back to the adapter
     * publish time ({@code ce_time}) if the extension is missing.
     */
    public String dataAgeLiteral() {
        if (sourceTime != null && !sourceTime.isBlank()) {
            return sourceTime;
        }
        return time == null ? null : DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(time);
    }

    public static IncomingEvent from(ConsumerRecord<String, CloudEvent> rec) {
        CloudEvent ce = rec.value();
        if (ce != null) {
            Object version = ce.getExtension("edaversion");
            Object trsOrder = ce.getExtension("edatrsorder");
            Object kind = ce.getExtension("edakind");
            Object sourceTime = ce.getExtension("edasourcetime");
            byte[] body = ce.getData() == null ? null : ce.getData().toBytes();
            return new IncomingEvent(
                    ce.getId(),
                    ce.getSource().toString(),
                    ce.getSubject(),
                    kind == null ? null : kind.toString(),
                    ce.getTime(),
                    sourceTime == null ? null : sourceTime.toString(),
                    version == null ? null : version.toString(),
                    trsOrder == null ? null : Long.parseLong(trsOrder.toString()),
                    body);
        }
        return new IncomingEvent(
                headerString(rec, "ce_id"),
                headerString(rec, "ce_source"),
                headerString(rec, "ce_subject"),
                headerString(rec, "ce_edakind"),
                parseTime(headerString(rec, "ce_time")),
                headerString(rec, "ce_edasourcetime"),
                headerString(rec, "ce_edaversion"),
                parseLong(headerString(rec, "ce_edatrsorder")),
                null);
    }

    private static String headerString(ConsumerRecord<?, ?> rec, String name) {
        Header h = rec.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static OffsetDateTime parseTime(String raw) {
        return raw == null ? null : OffsetDateTime.parse(raw);
    }

    private static Long parseLong(String raw) {
        return raw == null ? null : Long.valueOf(raw);
    }
}
