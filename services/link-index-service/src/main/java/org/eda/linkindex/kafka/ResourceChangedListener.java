package org.eda.linkindex.kafka;

import io.cloudevents.CloudEvent;
import java.nio.charset.StandardCharsets;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.eda.linkindex.state.LinkStateMachine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the resource.changed topics for both adapters. Each record is
 * normalised (null-value tombstones carry attributes only in headers, per
 * {@code docs/specs/asyncapi.yaml}) and handed to the state machine.
 */
@Component
public class ResourceChangedListener {

    private static final Logger log = LoggerFactory.getLogger(ResourceChangedListener.class);

    private final LinkStateMachine machine;

    public ResourceChangedListener(LinkStateMachine machine) {
        this.machine = machine;
    }

    @KafkaListener(
            id = "${eda.link-index.listener-id}",
            groupId = "${spring.kafka.consumer.group-id}",
            topics = {
                    "${eda.kafka.topics.polarion-changes}",
                    "${eda.kafka.topics.teamcenter-changes}"
            })
    public void onRecord(ConsumerRecord<String, CloudEvent> record, Acknowledgment ack) {
        try {
            CloudEvent event = record.value();
            String subject;
            String kind;
            String version;
            String ceId;
            if (event != null) {
                subject = event.getSubject();
                kind = valueOf(event.getExtension("edakind"));
                version = valueOf(event.getExtension("edaversion"));
                ceId = event.getId();
            } else {
                subject = header(record, "ce_subject");
                kind = header(record, "ce_edakind");
                version = header(record, "ce_edaversion");
                ceId = header(record, "ce_id");
            }
            if (subject == null || kind == null || ceId == null) {
                log.warn("skipping malformed record topic={} offset={} ceId={} subject={} kind={}",
                        record.topic(), record.offset(), ceId, subject, kind);
                ack.acknowledge();
                return;
            }
            machine.onResourceChanged(event, subject, kind, version, ceId);
            ack.acknowledge();
        } catch (RuntimeException e) {
            log.error("failed to process record topic={} offset={}: {}",
                    record.topic(), record.offset(), e.toString(), e);
            throw e;
        }
    }

    private static String header(ConsumerRecord<?, ?> rec, String name) {
        Header h = rec.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static String valueOf(Object ext) {
        return ext == null ? null : ext.toString();
    }
}
